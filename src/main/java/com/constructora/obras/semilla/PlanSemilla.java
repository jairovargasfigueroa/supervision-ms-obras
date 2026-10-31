package com.constructora.obras.semilla;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Zona;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.LineaPlanilla;
import com.constructora.obras.planilla.dominio.TotalesPlanilla;
import com.constructora.obras.semilla.DatosSemilla.ObraSemilla;
import com.constructora.obras.semilla.DatosSemilla.PartidaSemilla;
import com.constructora.obras.semilla.DatosSemilla.PlanillaSemilla;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Filas que la semilla escribe, ya resueltas con la fecha D: fechas relativas convertidas, insumos armados desde las
 * recetas, meses previstos con abreviaturas, montos de las líneas y totales de la planilla calculados con las clases
 * del dominio ({@link LineaPlanilla#de} y {@link TotalesPlanilla#calcular}) y número del cobro con la regla de
 * Finanzas (COB-AAAA-NNNN por año de emisión, en orden de emisión).
 *
 * <p>Comprueba lo que se puede copiar mal (docs/datos-prueba.md marca cada cuenta con ✓): presupuesto = monto del
 * contrato, acumulado de cada partida ≤ lo contratado, fotos adjuntas de la obra y del periodo, cobro solo en las
 * APROBADA. Si algo no cuadra, lanza {@link IllegalStateException} y no se carga nada.
 */
public final class PlanSemilla {

    private final LocalDate d;
    private final List<FilaObra> obras = new ArrayList<>();
    private final List<FilaPartida> partidas = new ArrayList<>();
    private final List<FilaInsumo> insumos = new ArrayList<>();
    private final List<FilaFoto> fotos = new ArrayList<>();
    private final List<FilaPlanilla> planillas = new ArrayList<>();

    private PlanSemilla(LocalDate d) {
        this.d = d;
    }

    /** Resuelve los datos con la fecha D de {@code fechas}. */
    public static PlanSemilla armar(DatosSemilla datos, FechaRelativa fechas) {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(fechas, "fechas");
        PlanSemilla plan = new PlanSemilla(fechas.d());
        Map<UUID, ObraSemilla> obrasPorId = new LinkedHashMap<>();
        Map<UUID, Map<String, PartidaSemilla>> partidasPorObra = new HashMap<>();
        for (ObraSemilla obra : datos.obras()) {
            if (obrasPorId.put(obra.id(), obra) != null) {
                throw error("La obra " + obra.id() + " está repetida");
            }
            partidasPorObra.put(obra.id(), plan.agregarObra(obra, datos, fechas));
        }
        Map<UUID, FilaFoto> fotosPorId = new HashMap<>();
        for (DatosSemilla.FotoSemilla foto : datos.fotos()) {
            PartidaSemilla partida = partidaDe(partidasPorObra, foto.obraId(), foto.partida(), "la foto " + foto.id());
            FilaFoto fila = new FilaFoto(foto.id(), foto.obraId(), partida.id(),
                    "fotos/" + foto.obraId() + "/" + foto.id() + ".jpg", foto.publicadaCliente(),
                    Objects.requireNonNull(datos.fotosRegistradasPor(), "fotosRegistradasPor"),
                    fechas.instante(foto.tomadaEn()));
            if (fotosPorId.put(foto.id(), fila) != null) {
                throw error("La foto " + foto.id() + " está repetida");
            }
            plan.fotos.add(fila);
        }
        Map<UUID, String> numerosCobro = numerosDeCobro(datos.planillas(), fechas);
        Map<UUID, Map<UUID, BigDecimal>> acumuladoPorObra = new HashMap<>();
        for (PlanillaSemilla planilla : datos.planillas()) {
            ObraSemilla obra = obrasPorId.get(planilla.obraId());
            if (obra == null) {
                throw error("La planilla " + planilla.id() + " es de una obra que no está en la semilla");
            }
            plan.planillas.add(plan.resolverPlanilla(planilla, obra, partidasPorObra.get(obra.id()), fotosPorId,
                    numerosCobro, acumuladoPorObra.computeIfAbsent(obra.id(), id -> new HashMap<>()), fechas));
        }
        return plan;
    }

    private Map<String, PartidaSemilla> agregarObra(ObraSemilla obra, DatosSemilla datos, FechaRelativa fechas) {
        EstadoObra.valueOf(obra.estado());
        if (obra.zona() != null) {
            Zona.valueOf(obra.zona());
        }
        obras.add(new FilaObra(obra.id(), obra.nombre(), obra.ubicacion(), obra.zona(), obra.estado(),
                obra.clienteUsuarioId(),
                obra.residenteUsuarioId(), obra.maestroUsuarioId(), obra.almaceneroUsuarioId(), obra.contratoId(),
                Dinero.de(obra.contrato().monto()).valor(), obra.contrato().plazoDias(),
                obra.contrato().anticipoPct(), obra.contrato().retencionPct(), fechas.fecha(obra.fechaInicio())));
        Map<String, PartidaSemilla> porCodigo = new LinkedHashMap<>();
        BigDecimal presupuesto = BigDecimal.ZERO;
        for (PartidaSemilla partida : obra.partidas()) {
            if (porCodigo.put(partida.codigo(), partida) != null) {
                throw error("La obra " + obra.id() + " repite el código de partida " + partida.codigo());
            }
            presupuesto = presupuesto.add(partida.cantidadContratada().multiply(partida.precioUnitario()));
            partidas.add(new FilaPartida(partida.id(), obra.id(), partida.codigo(), partida.nombre(),
                    partida.unidad(), partida.cantidadContratada(), partida.precioUnitario(),
                    fechas.meses(partida.meses())));
            if (partida.receta() != null) {
                Map<String, BigDecimal> receta = datos.recetas().get(partida.receta());
                if (receta == null) {
                    throw error("La partida " + partida.id() + " usa la receta «" + partida.receta()
                            + "», que no existe");
                }
                receta.forEach((codigoMaterial, cantidad) -> {
                    String materialId = datos.materiales().get(codigoMaterial);
                    if (materialId == null) {
                        throw error("La receta «" + partida.receta() + "» usa el material " + codigoMaterial
                                + ", que no tiene id");
                    }
                    insumos.add(new FilaInsumo(partida.id(), materialId, cantidad));
                });
            }
        }
        Dinero contrato = Dinero.de(obra.contrato().monto());
        if (Dinero.de(presupuesto).compareTo(contrato) != 0) {
            throw error("El presupuesto de la obra " + obra.id() + " (" + Dinero.de(presupuesto)
                    + ") no coincide con el monto del contrato (" + contrato + ")");
        }
        return porCodigo;
    }

    private FilaPlanilla resolverPlanilla(PlanillaSemilla planilla, ObraSemilla obra,
            Map<String, PartidaSemilla> partidasObra, Map<UUID, FilaFoto> fotosPorId, Map<UUID, String> numerosCobro,
            Map<UUID, BigDecimal> acumulado, FechaRelativa fechas) {
        EstadoPlanilla estado = EstadoPlanilla.valueOf(planilla.estado());
        Periodo periodo = new Periodo(fechas.periodo(planilla.periodo()));
        for (String codigo : planilla.cantidades().keySet()) {
            if (!partidasObra.containsKey(codigo)) {
                throw error("La planilla " + planilla.id() + " mide la partida " + codigo + ", que no es de su obra");
            }
        }
        // Una línea por partida de la obra (por código); las que no figuran van con cantidad 0
        List<LineaPlanilla> lineas = new ArrayList<>();
        partidasObra.values().stream().sorted(Comparator.comparing(PartidaSemilla::codigo)).forEach(partida -> {
            BigDecimal cantidad = planilla.cantidades().getOrDefault(partida.codigo(), BigDecimal.ZERO);
            BigDecimal total = acumulado.getOrDefault(partida.id(), BigDecimal.ZERO).add(cantidad);
            if (total.compareTo(partida.cantidadContratada()) > 0) {
                throw error("La planilla " + planilla.id() + " deja la partida " + partida.codigo()
                        + " por encima de lo contratado (" + total.toPlainString() + " de "
                        + partida.cantidadContratada().toPlainString() + ")");
            }
            acumulado.put(partida.id(), total);
            lineas.add(LineaPlanilla.de(partida.id(), cantidad, partida.precioUnitario()));
        });
        TotalesPlanilla totales = TotalesPlanilla.calcular(lineas.stream().map(LineaPlanilla::monto).toList(),
                obra.contrato().anticipoPct(), obra.contrato().retencionPct());
        Set<UUID> adjuntas = new HashSet<>();
        for (UUID fotoId : planilla.fotos()) {
            FilaFoto foto = fotosPorId.get(fotoId);
            if (foto == null || !foto.obraId().equals(obra.id())
                    || !Periodo.de(foto.registradoEn().atZone(FechaRelativa.ZONA).toLocalDate()).equals(periodo)) {
                throw error("La foto " + fotoId + " adjunta a la planilla " + planilla.id()
                        + " no es de su obra o no es del periodo " + periodo);
            }
            if (!adjuntas.add(fotoId)) {
                throw error("La planilla " + planilla.id() + " repite la foto " + fotoId);
            }
        }
        boolean aprobada = estado == EstadoPlanilla.APROBADA;
        if (aprobada != (planilla.cobro() != null)) {
            throw error("La planilla " + planilla.id() + " debe tener cobro si y solo si está APROBADA");
        }
        UUID cobroId = aprobada ? planilla.cobro().id() : null;
        return new FilaPlanilla(planilla.id(), obra.id(), planilla.numero(), periodo.primerDia(), estado.name(),
                totales, planilla.observacion(), cobroId, aprobada ? numerosCobro.get(cobroId) : null, 1,
                List.copyOf(lineas), List.copyOf(planilla.fotos()));
    }

    /**
     * Número de cada cobro (docs/datos-prueba.md, sección 8): COB-AAAA-NNNN con AAAA = año de la emisión en La Paz y
     * NNNN = correlativo por orden de emisión dentro de ese año.
     */
    private static Map<UUID, String> numerosDeCobro(List<PlanillaSemilla> planillas, FechaRelativa fechas) {
        record Emision(UUID cobroId, Instant emitidoEn) {
        }
        List<Emision> emisiones = planillas.stream()
                .filter(planilla -> planilla.cobro() != null)
                .map(planilla -> new Emision(planilla.cobro().id(), fechas.instante(planilla.cobro().emitidoEn())))
                .sorted(Comparator.comparing(Emision::emitidoEn))
                .toList();
        Map<Integer, Integer> correlativoPorAnio = new HashMap<>();
        Map<UUID, String> numeros = new HashMap<>();
        for (Emision emision : emisiones) {
            int anio = emision.emitidoEn().atZone(FechaRelativa.ZONA).getYear();
            int correlativo = correlativoPorAnio.merge(anio, 1, Integer::sum);
            if (numeros.put(emision.cobroId(), "COB-%d-%04d".formatted(anio, correlativo)) != null) {
                throw error("El cobro " + emision.cobroId() + " está en dos planillas");
            }
        }
        return numeros;
    }

    private static PartidaSemilla partidaDe(Map<UUID, Map<String, PartidaSemilla>> partidasPorObra, UUID obraId,
            String codigo, String quien) {
        Map<String, PartidaSemilla> partidas = partidasPorObra.get(obraId);
        PartidaSemilla partida = partidas == null ? null : partidas.get(codigo);
        if (partida == null) {
            throw error("La partida " + codigo + " de " + quien + " no existe en la obra " + obraId);
        }
        return partida;
    }

    private static IllegalStateException error(String mensaje) {
        return new IllegalStateException("Datos de la semilla incoherentes: " + mensaje);
    }

    public LocalDate d() {
        return d;
    }

    public List<FilaObra> obras() {
        return List.copyOf(obras);
    }

    public List<FilaPartida> partidas() {
        return List.copyOf(partidas);
    }

    public List<FilaInsumo> insumos() {
        return List.copyOf(insumos);
    }

    public List<FilaFoto> fotos() {
        return List.copyOf(fotos);
    }

    public List<FilaPlanilla> planillas() {
        return List.copyOf(planillas);
    }

    public record FilaObra(UUID id, String nombre, String ubicacion, String zona, String estado, UUID clienteUsuarioId,
            UUID residenteUsuarioId, UUID maestroUsuarioId, UUID almaceneroUsuarioId, UUID contratoId,
            BigDecimal montoContrato, int plazoDias, BigDecimal anticipoPct, BigDecimal retencionPct,
            LocalDate fechaInicio) {
    }

    public record FilaPartida(UUID id, UUID obraId, String codigo, String nombre, String unidad,
            BigDecimal cantidadContratada, BigDecimal precioUnitario, String meses) {
    }

    public record FilaInsumo(UUID partidaId, String materialId, BigDecimal cantidadPorUnidad) {
    }

    public record FilaFoto(UUID id, UUID obraId, UUID partidaId, String claveArchivo, boolean publicadaCliente,
            UUID registradoPor, Instant registradoEn) {
    }

    public record FilaPlanilla(UUID id, UUID obraId, int numero, LocalDate periodo, String estado,
            TotalesPlanilla totales, String observacion, UUID cobroId, String cobroNumero, int version,
            List<LineaPlanilla> lineas, List<UUID> fotoIds) {
    }
}
