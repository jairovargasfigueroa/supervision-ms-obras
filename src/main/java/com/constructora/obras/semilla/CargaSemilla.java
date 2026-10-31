package com.constructora.obras.semilla;

import com.constructora.obras.planilla.dominio.LineaPlanilla;
import com.constructora.obras.semilla.PlanSemilla.FilaFoto;
import com.constructora.obras.semilla.PlanSemilla.FilaInsumo;
import com.constructora.obras.semilla.PlanSemilla.FilaObra;
import com.constructora.obras.semilla.PlanSemilla.FilaPartida;
import com.constructora.obras.semilla.PlanSemilla.FilaPlanilla;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Escribe las filas de {@link PlanSemilla} en obras_db en una sola transacción (o todo o nada), con
 * {@code INSERT … ON CONFLICT DO NOTHING} por la clave primaria: lo que ya existe no se pisa y correrla dos veces no
 * duplica nada. No escribe en outbox_evento, mensaje_procesado ni saga_aprobacion (docs/datos-prueba.md, sección
 * 12). En modo recargar vacía antes todas las tablas del negocio, en la misma transacción.
 */
public class CargaSemilla {

    /** Tablas que carga la semilla, en el orden del resumen. */
    static final List<String> TABLAS = List.of("obra", "partida", "insumo_previsto", "foto_avance", "planilla",
            "linea_planilla", "planilla_foto");
    /** Historial de Flyway: recargar nunca lo vacía. */
    static final String HISTORIAL_MIGRACIONES = "flyway_schema_history";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public CargaSemilla(JdbcTemplate jdbc, TransactionTemplate transaccion) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transaccion = Objects.requireNonNull(transaccion, "transaccion");
    }

    /**
     * Arma las filas con la fecha D (si los datos no cuadran, falla antes de tocar la base) y las carga.
     *
     * @return cuántas filas se insertaron y cuántas hay en cada tabla al terminar
     */
    public ResumenSemilla ejecutar(ModoSemilla modo, FechaRelativa fechas) {
        Objects.requireNonNull(modo, "modo");
        PlanSemilla plan = PlanSemilla.armar(DatosSemilla.leer(), fechas);
        return transaccion.execute(estado -> {
            // En la misma transacción: si la carga falla, el vaciado también se deshace
            List<String> vaciadas = modo == ModoSemilla.RECARGAR ? vaciar() : List.of();
            Map<String, Integer> insertadas = cargar(plan);
            Map<String, ResumenSemilla.Conteo> conteos = new LinkedHashMap<>();
            for (String tabla : TABLAS) {
                conteos.put(tabla, new ResumenSemilla.Conteo(insertadas.get(tabla), contar(tabla)));
            }
            return new ResumenSemilla(fechas.d(), modo, vaciadas, conteos, contar("outbox_evento"));
        });
    }

    /**
     * Modo recargar: vacía todas las tablas del esquema salvo el historial de Flyway (también outbox_evento,
     * mensaje_procesado y saga_aprobacion, y las que agreguen las capas, como entrada_diario de la 103), con
     * {@code TRUNCATE … CASCADE}. La lista sale de pg_tables: una tabla nueva entra sola.
     *
     * @return tablas vaciadas, por nombre
     */
    private List<String> vaciar() {
        List<String> tablas = jdbc.queryForList("""
                select tablename from pg_tables
                where schemaname = current_schema() and tablename <> ?
                order by tablename""", String.class, HISTORIAL_MIGRACIONES);
        if (!tablas.isEmpty()) {
            jdbc.execute("truncate table " + tablas.stream().map(tabla -> "\"" + tabla.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(", ")) + " cascade");
        }
        return List.copyOf(tablas);
    }

    private Map<String, Integer> cargar(PlanSemilla plan) {
        Map<String, Integer> insertadas = new LinkedHashMap<>();
        TABLAS.forEach(tabla -> insertadas.put(tabla, 0));
        for (FilaObra obra : plan.obras()) {
            sumar(insertadas, "obra", jdbc.update("""
                    insert into obra (id, nombre, ubicacion, estado, cliente_usuario_id, residente_usuario_id,
                                      maestro_usuario_id, almacenero_usuario_id, contrato_id, monto_contrato,
                                      plazo_dias, anticipo_pct, retencion_pct, fecha_inicio)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do nothing""",
                    obra.id(), obra.nombre(), obra.ubicacion(), obra.estado(), obra.clienteUsuarioId(),
                    obra.residenteUsuarioId(), obra.maestroUsuarioId(), obra.almaceneroUsuarioId(), obra.contratoId(),
                    obra.montoContrato(), obra.plazoDias(), obra.anticipoPct(), obra.retencionPct(),
                    obra.fechaInicio()));
        }
        for (FilaPartida partida : plan.partidas()) {
            sumar(insertadas, "partida", jdbc.update("""
                    insert into partida (id, obra_id, codigo, nombre, unidad, cantidad_contratada, precio_unitario,
                                         meses)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do nothing""",
                    partida.id(), partida.obraId(), partida.codigo(), partida.nombre(), partida.unidad(),
                    partida.cantidadContratada(), partida.precioUnitario(), partida.meses()));
        }
        for (FilaInsumo insumo : plan.insumos()) {
            sumar(insertadas, "insumo_previsto", jdbc.update("""
                    insert into insumo_previsto (partida_id, material_id, cantidad_por_unidad)
                    values (?, ?, ?)
                    on conflict (partida_id, material_id) do nothing""",
                    insumo.partidaId(), insumo.materialId(), insumo.cantidadPorUnidad()));
        }
        for (FilaFoto foto : plan.fotos()) {
            sumar(insertadas, "foto_avance", jdbc.update("""
                    insert into foto_avance (id, obra_id, partida_id, clave_archivo, publicada_cliente, registrado_por,
                                             registrado_en)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do nothing""",
                    foto.id(), foto.obraId(), foto.partidaId(), foto.claveArchivo(), foto.publicadaCliente(),
                    foto.registradoPor(), OffsetDateTime.ofInstant(foto.registradoEn(), ZoneOffset.UTC)));
        }
        for (FilaPlanilla planilla : plan.planillas()) {
            sumar(insertadas, "planilla", jdbc.update("""
                    insert into planilla (id, obra_id, numero, periodo, estado, subtotal, descuento_anticipo,
                                          retencion, neto, observacion, cobro_id, cobro_numero, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do nothing""",
                    planilla.id(), planilla.obraId(), planilla.numero(), planilla.periodo(), planilla.estado(),
                    planilla.totales().subtotal().valor(), planilla.totales().descuentoAnticipo().valor(),
                    planilla.totales().retencion().valor(), planilla.totales().neto().valor(),
                    planilla.observacion(), planilla.cobroId(), planilla.cobroNumero(), planilla.version()));
            for (LineaPlanilla linea : planilla.lineas()) {
                sumar(insertadas, "linea_planilla", jdbc.update("""
                        insert into linea_planilla (planilla_id, partida_id, cantidad_periodo, monto)
                        values (?, ?, ?, ?)
                        on conflict (planilla_id, partida_id) do nothing""",
                        planilla.id(), linea.partidaId(), linea.cantidadPeriodo(), linea.monto().valor()));
            }
            for (UUID fotoId : planilla.fotoIds()) {
                sumar(insertadas, "planilla_foto", jdbc.update("""
                        insert into planilla_foto (planilla_id, foto_id)
                        values (?, ?)
                        on conflict (planilla_id, foto_id) do nothing""",
                        planilla.id(), fotoId));
            }
        }
        return insertadas;
    }

    private long contar(String tabla) {
        // El nombre sale de la lista fija de tablas, nunca de un dato
        Long total = jdbc.queryForObject("select count(*) from " + tabla, Long.class);
        return total == null ? 0 : total;
    }

    private static void sumar(Map<String, Integer> insertadas, String tabla, int filas) {
        insertadas.merge(tabla, filas, Integer::sum);
    }
}
