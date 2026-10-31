package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 011 · Los datos de prueba vistos por la API de Obras con D = 2026-10-28 (el reloj del servicio también está en ese
 * día): indicadores de las 3 obras y planillas (docs/datos-prueba.md, secciones 7 y 11), material previsto de la
 * obra 1 en 2026-09 y visibilidad de cada usuario de la sección 3. Antes de cada prueba se recarga.
 */
@Import(DatosPruebaIT.RelojDiaD.class)
class DatosPruebaIT extends PruebaIntegracion {

    static final LocalDate D = LocalDate.parse("2026-10-28");

    private static final String OBRA_1 = "b0000000-0000-4000-8000-000000000001";
    private static final String OBRA_2 = "b0000000-0000-4000-8000-000000000002";
    private static final String OBRA_3 = "b0000000-0000-4000-8000-000000000003";
    private static final String GERENTE1 = "a0000000-0000-4000-8000-000000000002";
    private static final String RESIDENTE1 = "a0000000-0000-4000-8000-000000000003";
    private static final String RESIDENTE2 = "a0000000-0000-4000-8000-000000000004";
    private static final String MAESTRO1 = "a0000000-0000-4000-8000-000000000005";
    private static final String ALMACENERO1 = "a0000000-0000-4000-8000-000000000006";
    private static final String CLIENTE1 = "a0000000-0000-4000-8000-000000000007";
    private static final String CLIENTE2 = "a0000000-0000-4000-8000-000000000008";
    private static final String DEMO1 = "a0000000-0000-4000-8000-000000000009";
    private static final String CEMENTO = "e0000000-0000-4000-8000-000000000001";
    private static final String FIERRO_12 = "e0000000-0000-4000-8000-000000000002";
    private static final String FIERRO_10 = "e0000000-0000-4000-8000-000000000003";
    private static final String LADRILLO = "e0000000-0000-4000-8000-000000000010";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaccion;
    @Autowired
    private JdbcConnectionDetails conexion;

    private CargaSemilla carga;

    /** El reloj del servicio en el día D (12:00 de La Paz): plazo transcurrido y periodos como el día de la carga. */
    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDiaD {

        @Bean
        @Primary
        Reloj relojDiaD() {
            Instant mediodia = D.atTime(12, 0).atZone(Reloj.ZONA_NEGOCIO).toInstant();
            return () -> mediodia;
        }
    }

    @BeforeEach
    void recargar() {
        carga = new CargaSemilla(jdbc, transaccion);
        carga.ejecutar(ModoSemilla.RECARGAR, new FechaRelativa(D));
    }

    @Test
    void cargarDosVecesNoDuplicaNadaNiEscribeEnElOutbox() {
        ResumenSemilla segunda = carga.ejecutar(ModoSemilla.CARGAR, new FechaRelativa(D));

        CargaSemilla.TABLAS.forEach(tabla -> assertThat(segunda.insertadas(tabla)).as(tabla).isZero());
        assertThat(segunda.total("obra")).isEqualTo(3);
        assertThat(segunda.total("partida")).isEqualTo(21);
        assertThat(segunda.total("insumo_previsto")).isEqualTo(58);
        assertThat(segunda.total("planilla")).isEqualTo(5);
        assertThat(segunda.total("linea_planilla")).isEqualTo(34);
        assertThat(segunda.total("foto_avance")).isEqualTo(10);
        assertThat(segunda.total("planilla_foto")).isEqualTo(5);
        assertThat(segunda.outboxEvento()).isZero();
        assertThat(contar("outbox_evento")).isZero();
        assertThat(contar("mensaje_procesado")).isZero();
        assertThat(contar("saga_aprobacion")).isZero();
    }

    @Test
    void cargarNoPisaLoQueCambioEnUnaDemostracion() {
        jdbc.update("update planilla set estado = 'APROBADA' where id = 'd0000000-0000-4000-8000-000000000103'");

        carga.ejecutar(ModoSemilla.CARGAR, new FechaRelativa(D));

        assertThat(jdbc.queryForObject(
                "select estado from planilla where id = 'd0000000-0000-4000-8000-000000000103'", String.class))
                .isEqualTo("APROBADA");
    }

    @Test
    void indicadoresDeLasTresObrasParaElGerente() throws Exception {
        pedir("/obras", TokensPrueba.valido(GERENTE1, "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // Por nombre: Condominio, Edificio, Unidad Educativa
                .andExpect(jsonPath("$[0].id").value(OBRA_2))
                .andExpect(jsonPath("$[0].nombre").value("Condominio Las Lomas (12 viviendas)"))
                .andExpect(jsonPath("$[0].zona").value("ESTE"))
                .andExpect(jsonPath("$[0].estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$[0].avancePct").value(16.34))
                .andExpect(jsonPath("$[0].plazoPct").value(50.0))
                .andExpect(jsonPath("$[0].enRiesgo").value(true))
                .andExpect(jsonPath("$[0].ultimaPlanilla.periodo").value("2026-09"))
                .andExpect(jsonPath("$[0].ultimaPlanilla.estado").value("OBSERVADA"))
                .andExpect(jsonPath("$[1].id").value(OBRA_1))
                .andExpect(jsonPath("$[1].nombre").value("Edificio Residencial Las Palmeras"))
                .andExpect(jsonPath("$[1].zona").value("NORTE"))
                .andExpect(jsonPath("$[1].fechaInicio").value("2026-06-30"))
                .andExpect(jsonPath("$[1].avancePct").value(28.49))
                .andExpect(jsonPath("$[1].plazoPct").value(33.33))
                .andExpect(jsonPath("$[1].enRiesgo").value(false))
                .andExpect(jsonPath("$[1].ultimaPlanilla.periodo").value("2026-09"))
                .andExpect(jsonPath("$[1].ultimaPlanilla.estado").value("PRESENTADA"))
                .andExpect(jsonPath("$[2].id").value(OBRA_3))
                .andExpect(jsonPath("$[2].nombre").value("Unidad Educativa Nueva Esperanza"))
                .andExpect(jsonPath("$[2].zona").value("SUR"))
                .andExpect(jsonPath("$[2].avancePct").value(0.0))
                .andExpect(jsonPath("$[2].plazoPct").value(2.08))
                .andExpect(jsonPath("$[2].enRiesgo").value(false))
                .andExpect(jsonPath("$[2].ultimaPlanilla").isEmpty());
    }

    @Test
    void detalleDeLaObra1ConElPresupuestoIgualAlContrato() throws Exception {
        pedir("/obras/" + OBRA_1, TokensPrueba.valido(GERENTE1, "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contrato.contratoId").value("c0000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.contrato.monto").value(2773800.0))
                .andExpect(jsonPath("$.contrato.plazoDias").value(360))
                .andExpect(jsonPath("$.contrato.anticipoPct").value(10.0))
                .andExpect(jsonPath("$.contrato.retencionPct").value(7.0))
                .andExpect(jsonPath("$.asignaciones.residenteId").value(RESIDENTE1))
                .andExpect(jsonPath("$.asignaciones.clienteId").value(CLIENTE1))
                .andExpect(jsonPath("$.partidas", hasSize(6)))
                .andExpect(jsonPath("$.partidas[1].codigo").value("P-02"))
                .andExpect(jsonPath("$.partidas[1].meses").value("jul-ago"))
                .andExpect(jsonPath("$.partidas[1].ejecutadaAcumulada").value(180.0))
                .andExpect(jsonPath("$.partidas[1].avancePct").value(100.0))
                .andExpect(jsonPath("$.partidas[2].avancePct").value(35.71))
                .andExpect(jsonPath("$.partidas[3].avancePct").value(12.5))
                .andExpect(jsonPath("$.partidas[2].insumos", hasSize(4)));
        Double presupuesto = jdbc.queryForObject("""
                select sum(cantidad_contratada * precio_unitario) from partida where obra_id = ?::uuid""",
                Double.class, OBRA_1);
        assertThat(presupuesto).isEqualTo(2773800.0);
    }

    @Test
    void totalesDeLasCincoPlanillas() throws Exception {
        planilla("d0000000-0000-4000-8000-000000000101", "APROBADA", 349560.00, 34956.00, 24469.20, 290134.80)
                .andExpect(jsonPath("$.periodo").value("2026-07"))
                .andExpect(jsonPath("$.cobroId").value("c2000000-0000-4000-8000-000000000101"))
                .andExpect(jsonPath("$.cobroNumero").value("COB-2026-0001"));
        planilla("d0000000-0000-4000-8000-000000000102", "APROBADA", 440680.00, 44068.00, 30847.60, 365764.40)
                .andExpect(jsonPath("$.cobroNumero").value("COB-2026-0003"));
        planilla("d0000000-0000-4000-8000-000000000103", "PRESENTADA", 397770.00, 39777.00, 27843.90, 330149.10)
                .andExpect(jsonPath("$.periodo").value("2026-09"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cobroId").isEmpty())
                .andExpect(jsonPath("$.lineas", hasSize(6)))
                .andExpect(jsonPath("$.lineas[2].codigo").value("P-03"))
                .andExpect(jsonPath("$.lineas[2].acumuladoAnterior").value(50.0))
                .andExpect(jsonPath("$.lineas[2].cantidadPeriodo").value(40.0))
                .andExpect(jsonPath("$.lineas[2].monto").value(134600.0))
                .andExpect(jsonPath("$.fotoIds", contains("f1000000-0000-4000-8000-000000010302",
                        "f1000000-0000-4000-8000-000000010402", "f1000000-0000-4000-8000-000000010502")));
        planilla("d0000000-0000-4000-8000-000000000201", "APROBADA", 520340.00, 78051.00, 36423.80, 405865.20)
                .andExpect(jsonPath("$.cobroNumero").value("COB-2026-0002"));
        planilla("d0000000-0000-4000-8000-000000000202", "OBSERVADA", 131600.00, 19740.00, 9212.00, 102648.00)
                .andExpect(jsonPath("$.observacion").value("La medición de columnas y vigas (40 m³) no coincide con "
                        + "lo verificado en la visita de obra, que fue de unos 30 m³. Corrijan la cantidad y adjunten "
                        + "fotos de los vaciados."))
                .andExpect(jsonPath("$.fotoIds", hasSize(2)));
    }

    @Test
    void materialPrevistoDeLaObra1EnSeptiembre() throws Exception {
        String p03 = "b1000000-0000-4000-8000-000000000103";
        pedir("/obras/" + OBRA_1 + "/material-previsto?periodo=2026-09", TokensPrueba.valido(GERENTE1, "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayPlanilla").value(true))
                .andExpect(jsonPath("$.items[?(@.partidaId == '" + p03 + "' && @.materialId == '" + FIERRO_12
                        + "')].cantidadPrevista", contains(4800.0)))
                .andExpect(jsonPath("$.items[?(@.partidaId == 'b1000000-0000-4000-8000-000000000104' && "
                        + "@.materialId == '" + FIERRO_10 + "')].cantidadPrevista", contains(4500.0)))
                .andExpect(jsonPath("$.items[?(@.partidaId == 'b1000000-0000-4000-8000-000000000105' && "
                        + "@.materialId == '" + LADRILLO + "')].cantidadPrevista", contains(12000.0)));
        pedir("/obras/" + OBRA_2 + "/material-previsto?periodo=2026-09", TokensPrueba.valido(RESIDENTE2, "residente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.partidaId == 'b1000000-0000-4000-8000-000000000204' && "
                        + "@.materialId == '" + CEMENTO + "')].cantidadPrevista", contains(300.0)));
    }

    @Test
    void visibilidadDeCadaUsuario() throws Exception {
        obrasVisibles(TokensPrueba.valido(RESIDENTE1, "residente"), OBRA_1);
        obrasVisibles(TokensPrueba.valido(RESIDENTE2, "residente"), OBRA_2);
        // demo1 tiene residente y cliente: en el ERP cuenta residente (obra 3); en el portal, cliente (obra 2)
        obrasVisibles(TokensPrueba.valido(DEMO1, "residente", "cliente"), OBRA_3);
        obrasVisibles(TokensPrueba.desde("portal", DEMO1, "residente", "cliente"), OBRA_2);
        obrasVisibles(TokensPrueba.desde("portal", CLIENTE1, "cliente"), OBRA_1, OBRA_3);
        obrasVisibles(TokensPrueba.desde("portal", CLIENTE2, "cliente"));
        obrasVisibles(TokensPrueba.desde("app-obra", MAESTRO1, "maestro"), OBRA_2, OBRA_1, OBRA_3);
        obrasVisibles(TokensPrueba.desde("app-obra", ALMACENERO1, "almacenero"), OBRA_2, OBRA_1, OBRA_3);
    }

    @Test
    void fotosDeCadaObraYLasPublicadasParaElCliente() throws Exception {
        pedir("/obras/" + OBRA_1 + "/fotos", TokensPrueba.valido(GERENTE1, "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6));
        pedir("/obras/" + OBRA_3 + "/fotos", TokensPrueba.valido(DEMO1, "residente", "cliente"))
                .andExpect(jsonPath("$.total").value(2));
        pedir("/obras/" + OBRA_1 + "/fotos", TokensPrueba.desde("portal", CLIENTE1, "cliente"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].publicadaCliente", contains(true, true, true)));
        pedir("/obras/" + OBRA_2 + "/fotos", TokensPrueba.desde("portal", DEMO1, "residente", "cliente"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void laGuardaAceptaLaBaseDeLasPruebas() {
        GuardaSemilla.verificar(false, conexion.getJdbcUrl());
    }

    private ResultActions planilla(String id, String estado, double subtotal, double anticipo, double retencion,
            double neto) throws Exception {
        return pedir("/planillas/" + id, TokensPrueba.valido(GERENTE1, "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.subtotal").value(subtotal))
                .andExpect(jsonPath("$.descuentoAnticipo").value(anticipo))
                .andExpect(jsonPath("$.retencion").value(retencion))
                .andExpect(jsonPath("$.neto").value(neto));
    }

    private void obrasVisibles(String token, String... obras) throws Exception {
        ResultActions respuesta = pedir("/obras", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(obras.length)));
        if (obras.length > 0) {
            respuesta.andExpect(jsonPath("$[*].id", contains((Object[]) obras)));
        }
    }

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }
}
