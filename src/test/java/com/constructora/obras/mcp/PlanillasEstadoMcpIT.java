package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;

/**
 * 501 · planillas_estado: estados por defecto (todos menos APROBADA), orden por periodo, días desde la presentación
 * (solo PRESENTADA, La Paz), observación recortada, filtros, límite y las cifras de GET /obras/{obraId}/planillas.
 */
@SuppressWarnings("unchecked")
class PlanillasEstadoMcpIT extends PruebaIntegracion {

    private static final String GERENTE = "a0000000-0000-4000-8000-000000000002";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Reloj reloj;

    private UUID obra;
    private UUID aprobada;
    private UUID observada;
    private UUID presentada;
    private UUID cobro;
    private Instant presentadaEn;
    private YearMonth mes;

    @BeforeEach
    void planillas() throws Exception {
        mes = YearMonth.from(reloj.hoy());
        obra = ObrasPrueba.registrar(mvc, "Planillas MCP " + UUID.randomUUID(), Equipo.nuevo());
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        UUID losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        aprobada = PlanillasPrueba.insertar(jdbc, obra, 1, mes.minusMonths(3).toString(), "APROBADA",
                Map.of(losa, BigDecimal.ONE));
        observada = PlanillasPrueba.insertar(jdbc, obra, 2, mes.minusMonths(2).toString(), "OBSERVADA",
                Map.of(losa, BigDecimal.ONE));
        presentada = PlanillasPrueba.insertar(jdbc, obra, 3, mes.minusMonths(1).toString(), "PRESENTADA",
                Map.of(losa, BigDecimal.ONE));
        cobro = UUID.randomUUID();
        jdbc.update("update planilla set cobro_id = ?, cobro_numero = 'COB-2026-0099', neto = 100.50 where id = ?",
                cobro, aprobada);
        jdbc.update("update planilla set observacion = ?, neto = 20000.00 where id = ?", "a".repeat(600), observada);
        // Presentada hace 9 días a las 23:30 de La Paz (el día siguiente en UTC): cuenta el día de La Paz
        presentadaEn = reloj.hoy().minusDays(9).atTime(LocalTime.of(23, 30)).atZone(Reloj.ZONA_NEGOCIO).toInstant();
        jdbc.update("update planilla set presentada_en = ?, neto = 41881.80 where id = ?",
                Timestamp.from(presentadaEn), presentada);
    }

    @Test
    void porDefectoTodasMenosLasAprobadasDeLaMasRecienteALaMasAntigua() throws Exception {
        Map<String, Object> salida = llamar(Map.of("obraId", obra.toString()));
        List<Map<String, Object>> planillas = (List<Map<String, Object>>) salida.get("planillas");

        assertThat(planillas).extracting(fila -> fila.get("planillaId"))
                .containsExactly(presentada.toString(), observada.toString());
        Map<String, Object> primera = planillas.getFirst();
        assertThat(primera).containsEntry("obraId", obra.toString()).containsEntry("numero", 3)
                .containsEntry("periodo", mes.minusMonths(1).toString()).containsEntry("estado", "PRESENTADA")
                .containsEntry("observacion", null).containsEntry("cobroId", null)
                .containsEntry("presentadaEn", presentadaEn.toString()).containsEntry("diasDesdePresentacion", 9);
        assertThat((String) primera.get("obraNombre")).startsWith("Planillas MCP");
        assertThat(new BigDecimal(String.valueOf(primera.get("neto")))).isEqualByComparingTo("41881.80");
        Map<String, Object> segunda = planillas.get(1);
        assertThat((String) segunda.get("observacion")).hasSize(HerramientasMcpObras.OBSERVACION_MAXIMA);
        assertThat(segunda).containsEntry("diasDesdePresentacion", null).containsEntry("presentadaEn", null);
        assertThat(salida).containsEntry("truncado", false);
    }

    @Test
    void lasAprobadasConSuCobroYLasMismasCifrasQueElRest() throws Exception {
        Map<String, Object> salida = llamar(Map.of("obraId", obra.toString(), "estados",
                List.of("APROBADA", "PRESENTADA", "OBSERVADA")));
        List<Map<String, Object>> planillas = (List<Map<String, Object>>) salida.get("planillas");
        assertThat(planillas).hasSize(3);
        assertThat(planillas.getLast()).containsEntry("planillaId", aprobada.toString())
                .containsEntry("cobroId", cobro.toString()).containsEntry("diasDesdePresentacion", null);

        String rest = mvc.perform(get("/obras/{id}/planillas", obra)
                        .header("Authorization", "Bearer " + TokensPrueba.valido(GERENTE, "gerente")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> deRest = McpPrueba.json().readValue(rest,
                new TypeReference<List<Map<String, Object>>>() {
                });
        for (Map<String, Object> planilla : planillas) {
            Map<String, Object> igual = deRest.stream().filter(fila -> fila.get("id").equals(planilla.get("planillaId")))
                    .findFirst().orElseThrow();
            assertThat(planilla.get("estado")).isEqualTo(igual.get("estado"));
            assertThat(planilla.get("numero")).isEqualTo(igual.get("numero"));
            assertThat(planilla.get("periodo")).isEqualTo(igual.get("periodo"));
            assertThat(new BigDecimal(String.valueOf(planilla.get("neto"))))
                    .isEqualByComparingTo(new BigDecimal(String.valueOf(igual.get("neto"))));
        }
    }

    @Test
    void desdePeriodoYLimite() throws Exception {
        assertThat((List<Map<String, Object>>) llamar(Map.of("obraId", obra.toString(), "desdePeriodo",
                mes.minusMonths(1).toString())).get("planillas")).extracting(fila -> fila.get("planillaId"))
                .containsExactly(presentada.toString());
        Map<String, Object> recortada = llamar(Map.of("obraId", obra.toString(), "limite", 1));
        assertThat((List<?>) recortada.get("planillas")).hasSize(1);
        assertThat(recortada).containsEntry("truncado", true);
    }

    @Test
    void sinObraIdLasDeTodasLasObrasPorPeriodo() throws Exception {
        List<Map<String, Object>> planillas = (List<Map<String, Object>>) llamar(Map.of("limite", 50))
                .get("planillas");
        assertThat(planillas).noneMatch(fila -> fila.get("estado").equals("APROBADA"));
        assertThat(planillas.stream().map(fila -> (String) fila.get("periodo")).toList())
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    void erroresDeArgumentosYObraInexistente() throws Exception {
        assertThat(codigo(Map.of("obraId", UUID.randomUUID().toString()))).isEqualTo("OBRA_NO_ENCONTRADA");
        assertThat(codigo(Map.of("estados", List.of("CERRADA")))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("estados", List.of()))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("desdePeriodo", "2027-13"))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("limite", 51))).isEqualTo("ARGUMENTO_INVALIDO");
    }

    private Map<String, Object> llamar(Map<String, Object> argumentos) throws Exception {
        return McpPrueba.salida(McpPrueba.llamar(mvc, "planillas_estado", argumentos));
    }

    private Object codigo(Map<String, Object> argumentos) throws Exception {
        return McpPrueba.error(McpPrueba.llamar(mvc, "planillas_estado", argumentos)).get("codigo");
    }
}
