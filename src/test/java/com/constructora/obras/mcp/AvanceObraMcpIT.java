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
 * 501 · avance_obra: mismas cifras que GET /obras, GET /obras/{id} y GET /obras/{id}/material-previsto (006), orden
 * por diferencia, partidas y material previsto solo con obraId y periodo, y truncado.
 */
@SuppressWarnings("unchecked")
class AvanceObraMcpIT extends PruebaIntegracion {

    private static final String GERENTE = "a0000000-0000-4000-8000-000000000002";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Reloj reloj;

    private UUID obra;
    private UUID sinContrato;
    private YearMonth mes;

    @BeforeEach
    void obras() throws Exception {
        mes = YearMonth.from(reloj.hoy());
        obra = ObrasPrueba.registrarDosPartidas(mvc, "Avance MCP " + UUID.randomUUID(), Equipo.nuevo());
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        PlanillasPrueba.cambiarInicio(jdbc, obra, reloj.hoy().minusDays(50));
        UUID losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        UUID muro = PlanillasPrueba.partida(jdbc, obra, "P-02");
        PlanillasPrueba.insertar(jdbc, obra, 1, mes.minusMonths(1).toString(), "APROBADA",
                Map.of(losa, new BigDecimal("3"), muro, new BigDecimal("4")));
        PlanillasPrueba.insertar(jdbc, obra, 2, mes.toString(), "BORRADOR",
                Map.of(losa, new BigDecimal("2"), muro, BigDecimal.ONE));
        sinContrato = ObrasPrueba.registrar(mvc, "Sin contrato MCP " + UUID.randomUUID(), Equipo.nuevo());
    }

    @Test
    void sinObraIdLasObrasEnEjecucionConLasCifrasDelRest() throws Exception {
        Map<String, Object> salida = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra", Map.of()));
        List<Map<String, Object>> obras = (List<Map<String, Object>>) salida.get("obras");

        Map<String, Object> deMcp = buscar(obras, obra);
        Map<String, Object> deRest = buscar(rest("/obras"), obra);
        assertThat(numero(deMcp, "avancePct")).isEqualByComparingTo(numero(deRest, "avancePct"))
                .isEqualByComparingTo("25.00");
        assertThat(numero(deMcp, "plazoPct")).isEqualByComparingTo(numero(deRest, "plazoPct"))
                .isEqualByComparingTo("50.00");
        assertThat(numero(deMcp, "diferencia")).isEqualByComparingTo("25.00");
        assertThat(deMcp.get("enRiesgo")).isEqualTo(deRest.get("enRiesgo")).isEqualTo(true);
        assertThat(deMcp.get("ultimaPlanilla")).isEqualTo(Map.of("periodo", mes.toString(), "estado", "BORRADOR"));
        assertThat(deMcp).containsEntry("estado", "EN_EJECUCION").containsEntry("plazoDias", 100)
                .containsEntry("fechaInicio", reloj.hoy().minusDays(50).toString()).containsEntry("partidas", null);
        assertThat(obras).noneMatch(fila -> fila.get("obraId").equals(sinContrato.toString()))
                .allMatch(fila -> fila.get("estado").equals("EN_EJECUCION"));
        // De la más atrasada a la menos
        List<BigDecimal> diferencias = obras.stream().map(fila -> numero(fila, "diferencia")).toList();
        assertThat(diferencias).isSortedAccordingTo((a, b) -> b.compareTo(a));
        assertThat(salida).containsEntry("hayPlanillaEnPeriodo", null).containsKey("generadoEn");
    }

    @Test
    void conObraIdYPeriodoLasPartidasYElMaterialPrevistoDelRest() throws Exception {
        Map<String, Object> salida = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra",
                Map.of("obraId", obra.toString(), "periodo", mes.toString())));
        List<Map<String, Object>> obras = (List<Map<String, Object>>) salida.get("obras");
        assertThat(obras).hasSize(1);
        assertThat(salida).containsEntry("hayPlanillaEnPeriodo", true).containsEntry("truncado", false);
        List<Map<String, Object>> partidas = (List<Map<String, Object>>) obras.getFirst().get("partidas");
        assertThat(partidas).extracting(fila -> fila.get("codigo")).containsExactly("P-01", "P-02");

        Map<String, Object> detalle = restObjeto("/obras/" + obra);
        for (Map<String, Object> partida : partidas) {
            Map<String, Object> deRest = ((List<Map<String, Object>>) detalle.get("partidas")).stream()
                    .filter(fila -> fila.get("id").equals(partida.get("partidaId"))).findFirst().orElseThrow();
            assertThat(numero(partida, "cantidadContratada")).isEqualByComparingTo(numero(deRest,
                    "cantidadContratada"));
            assertThat(numero(partida, "ejecutadaAcumulada")).isEqualByComparingTo(numero(deRest,
                    "ejecutadaAcumulada"));
            assertThat(numero(partida, "avancePct")).isEqualByComparingTo(numero(deRest, "avancePct"));
        }
        assertThat(numero(partidas.getFirst(), "avancePct")).isEqualByComparingTo("30.00");

        List<Map<String, Object>> previstoRest = (List<Map<String, Object>>) restObjeto(
                "/obras/" + obra + "/material-previsto?periodo=" + mes).get("items");
        List<Map<String, Object>> previstoMcp = partidas.stream()
                .flatMap(partida -> ((List<Map<String, Object>>) partida.get("materialPrevisto")).stream()
                        .map(item -> Map.of("partidaId", partida.get("partidaId"), "materialId",
                                item.get("materialId"), "cantidad", numero(item, "cantidadPrevista"))))
                .toList();
        assertThat(previstoMcp).hasSize(previstoRest.size()).hasSize(3);
        for (Map<String, Object> item : previstoRest) {
            assertThat(previstoMcp).anySatisfy(fila -> {
                assertThat(fila.get("partidaId")).isEqualTo(item.get("partidaId"));
                assertThat(fila.get("materialId")).isEqualTo(item.get("materialId"));
                assertThat((BigDecimal) fila.get("cantidad")).isEqualByComparingTo(numero(item, "cantidadPrevista"));
            });
        }
    }

    @Test
    void sinPlanillaEnElPeriodoElMaterialPrevistoVaVacio() throws Exception {
        Map<String, Object> salida = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra",
                Map.of("obraId", obra.toString(), "periodo", mes.minusMonths(5).toString())));
        assertThat(salida).containsEntry("hayPlanillaEnPeriodo", false);
        List<Map<String, Object>> partidas = (List<Map<String, Object>>) ((List<Map<String, Object>>) salida
                .get("obras")).getFirst().get("partidas");
        assertThat(partidas).allMatch(partida -> ((List<?>) partida.get("materialPrevisto")).isEmpty());
    }

    @Test
    void conObraIdSinPeriodoCualquierEstadoYSinMaterialPrevisto() throws Exception {
        Map<String, Object> salida = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra",
                Map.of("obraId", sinContrato.toString())));
        Map<String, Object> unica = ((List<Map<String, Object>>) salida.get("obras")).getFirst();
        assertThat(unica).containsEntry("estado", "SIN_CONTRATO").containsEntry("ultimaPlanilla", null);
        assertThat((List<Map<String, Object>>) unica.get("partidas"))
                .allMatch(partida -> partida.get("materialPrevisto") == null);
        assertThat(salida).containsEntry("hayPlanillaEnPeriodo", null);
    }

    @Test
    void periodoSinObraIdEsArgumentoInvalido() throws Exception {
        assertThat(McpPrueba.error(McpPrueba.llamar(mvc, "avance_obra", Map.of("periodo", mes.toString()))))
                .containsEntry("codigo", "ARGUMENTO_INVALIDO");
    }

    @Test
    void masDeCincuentaObrasEnEjecucionSeRecortan() throws Exception {
        Integer enEjecucion = jdbc.queryForObject("select count(*) from obra where estado = 'EN_EJECUCION'",
                Integer.class);
        for (int i = enEjecucion; i <= HerramientasMcpObras.MAX_OBRAS; i++) {
            UUID otra = ObrasPrueba.registrar(mvc, "Relleno MCP " + UUID.randomUUID(), Equipo.nuevo());
            PlanillasPrueba.cambiarEstado(jdbc, otra, "EN_EJECUCION");
        }

        Map<String, Object> salida = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra", Map.of()));

        assertThat((List<?>) salida.get("obras")).hasSize(HerramientasMcpObras.MAX_OBRAS);
        assertThat(salida).containsEntry("truncado", true);
    }

    private static Map<String, Object> buscar(List<Map<String, Object>> filas, UUID id) {
        return filas.stream()
                .filter(fila -> id.toString().equals(fila.get("obraId") != null ? fila.get("obraId") : fila.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("No está la obra " + id));
    }

    private List<Map<String, Object>> rest(String ruta) throws Exception {
        return McpPrueba.json().readValue(pedirRest(ruta), new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private Map<String, Object> restObjeto(String ruta) throws Exception {
        return McpPrueba.json().readValue(pedirRest(ruta), new TypeReference<Map<String, Object>>() {
        });
    }

    private String pedirRest(String ruta) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + TokensPrueba.valido(GERENTE, "gerente")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal numero(Map<String, Object> fila, String campo) {
        return new BigDecimal(String.valueOf(fila.get(campo)));
    }
}
