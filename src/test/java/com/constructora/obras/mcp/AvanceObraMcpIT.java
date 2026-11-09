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
import java.util.HashMap;
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
 * por diferencia, partidas y material previsto con obraId y periodo, y truncado. 505 · periodo sin obraId: el lote de
 * obras en ejecución con las partidas reducidas al material previsto (sin las que no tienen).
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
    void periodoSinObraIdDevuelveElLoteConLasPartidasReducidasYElMaterialPrevisto() throws Exception {
        // Plazo cumplido y sin avance (diferencia 100, la mayor) y un nombre que va primero: entran en las 50
        String prefijo = "0 Lote MCP " + UUID.randomUUID() + " ";
        UUID conPlanilla = obraAtrasada(prefijo + "A");
        UUID losaA = PlanillasPrueba.partida(jdbc, conPlanilla, "P-01");
        UUID muroA = PlanillasPrueba.partida(jdbc, conPlanilla, "P-02");
        jdbc.update("delete from insumo_previsto where partida_id = ?", muroA);
        PlanillasPrueba.insertar(jdbc, conPlanilla, 1, mes.toString(), "BORRADOR",
                Map.of(losaA, new BigDecimal("2"), muroA, BigDecimal.ONE));
        UUID sinMuro = obraAtrasada(prefijo + "B");
        PlanillasPrueba.insertar(jdbc, sinMuro, 1, mes.toString(), "PRESENTADA",
                Map.of(PlanillasPrueba.partida(jdbc, sinMuro, "P-01"), BigDecimal.ONE));
        UUID sinPlanilla = obraAtrasada(prefijo + "C");
        PlanillasPrueba.insertar(jdbc, sinPlanilla, 1, mes.minusMonths(1).toString(), "BORRADOR",
                Map.of(PlanillasPrueba.partida(jdbc, sinPlanilla, "P-01"), BigDecimal.ONE));
        UUID concluida = obraAtrasada(prefijo + "D");
        PlanillasPrueba.cambiarEstado(jdbc, concluida, "CONCLUIDA");
        PlanillasPrueba.insertar(jdbc, concluida, 1, mes.toString(), "APROBADA",
                Map.of(PlanillasPrueba.partida(jdbc, concluida, "P-01"), BigDecimal.ONE));

        Map<String, Object> lote = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra",
                Map.of("periodo", mes.toString())));
        Map<String, Object> sinPeriodo = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra", Map.of()));

        List<Map<String, Object>> obras = (List<Map<String, Object>>) lote.get("obras");
        List<Map<String, Object>> obrasSinPeriodo = (List<Map<String, Object>>) sinPeriodo.get("obras");
        // Mismas obras, mismo orden, mismos datos de la obra y mismo truncado que sin periodo
        assertThat(obras).extracting(fila -> fila.get("obraId"))
                .containsExactlyElementsOf(obrasSinPeriodo.stream().map(fila -> fila.get("obraId")).toList())
                .contains(conPlanilla.toString(), sinMuro.toString(), sinPlanilla.toString())
                .doesNotContain(concluida.toString(), sinContrato.toString());
        assertThat(obras).allMatch(fila -> fila.get("estado").equals("EN_EJECUCION"));
        for (int i = 0; i < obras.size(); i++) {
            Map<String, Object> conPartidas = new HashMap<>(obras.get(i));
            assertThat(conPartidas.remove("partidas")).isInstanceOf(List.class);
            Map<String, Object> esperada = new HashMap<>(obrasSinPeriodo.get(i));
            esperada.remove("partidas");
            assertThat(conPartidas).isEqualTo(esperada);
        }
        assertThat(lote).containsEntry("hayPlanillaEnPeriodo", null)
                .containsEntry("truncado", sinPeriodo.get("truncado"));
        List<String> ids = obras.stream().map(fila -> (String) fila.get("obraId")).toList();
        assertThat(ids.indexOf(conPlanilla.toString())).isLessThan(ids.indexOf(sinMuro.toString()));
        assertThat(ids.indexOf(sinMuro.toString())).isLessThan(ids.indexOf(sinPlanilla.toString()));

        // Cada partida solo con partidaId, codigo, nombre y materialPrevisto (nunca vacío)
        obras.stream().flatMap(fila -> ((List<Map<String, Object>>) fila.get("partidas")).stream())
                .forEach(partida -> {
                    assertThat(partida).containsOnlyKeys("partidaId", "codigo", "nombre", "materialPrevisto");
                    assertThat((List<?>) partida.get("materialPrevisto")).isNotEmpty();
                });
        // P-02 sin insumos se omite; P-01 con el material previsto del REST (2 × 1,1 y 2 × 2,5)
        List<Map<String, Object>> partidasA = partidas(obras, conPlanilla);
        assertThat(partidasA).extracting(fila -> fila.get("codigo")).containsExactly("P-01");
        assertThat(partidasA.getFirst()).containsEntry("partidaId", losaA.toString()).containsEntry("nombre", "Losa");
        assertThat(previsto(partidasA)).containsExactlyInAnyOrderElementsOf(previstoRest(conPlanilla))
                .containsExactly(List.of(losaA.toString(), "cemento", "2.2"), List.of(losaA.toString(), "fierro", "5"));
        // P-02 sin medir en la planilla del periodo: va con su material previsto en 0, como con una obra
        List<Map<String, Object>> partidasB = partidas(obras, sinMuro);
        assertThat(partidasB).extracting(fila -> fila.get("codigo")).containsExactly("P-01", "P-02");
        assertThat(previsto(partidasB)).containsExactlyInAnyOrderElementsOf(previstoRest(sinMuro));
        // Sin planilla en el periodo: ninguna partida tiene material previsto
        assertThat(partidas(obras, sinPlanilla)).isEmpty();
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
        Map<String, Object> lote = McpPrueba.salida(McpPrueba.llamar(mvc, "avance_obra",
                Map.of("periodo", mes.toString())));

        assertThat((List<?>) salida.get("obras")).hasSize(HerramientasMcpObras.MAX_OBRAS);
        assertThat(salida).containsEntry("truncado", true);
        assertThat((List<?>) lote.get("obras")).hasSize(HerramientasMcpObras.MAX_OBRAS);
        assertThat(lote).containsEntry("truncado", true);
    }

    /** Obra de dos partidas en ejecución con el plazo cumplido y sin avance aprobado: diferencia 100. */
    private UUID obraAtrasada(String nombre) throws Exception {
        UUID id = ObrasPrueba.registrarDosPartidas(mvc, nombre, Equipo.nuevo());
        PlanillasPrueba.cambiarEstado(jdbc, id, "EN_EJECUCION");
        PlanillasPrueba.cambiarInicio(jdbc, id, reloj.hoy().minusDays(100));
        return id;
    }

    private static List<Map<String, Object>> partidas(List<Map<String, Object>> obras, UUID obraId) {
        return (List<Map<String, Object>>) buscar(obras, obraId).get("partidas");
    }

    /** [partidaId, materialId, cantidad sin ceros a la derecha] de cada material previsto de las partidas. */
    private static List<List<String>> previsto(List<Map<String, Object>> partidas) {
        return partidas.stream()
                .flatMap(partida -> ((List<Map<String, Object>>) partida.get("materialPrevisto")).stream()
                        .map(item -> List.of((String) partida.get("partidaId"), (String) item.get("materialId"),
                                numero(item, "cantidadPrevista").stripTrailingZeros().toPlainString())))
                .toList();
    }

    /** Lo mismo, de GET /obras/{id}/material-previsto del periodo. */
    private List<List<String>> previstoRest(UUID obraId) throws Exception {
        List<Map<String, Object>> items = (List<Map<String, Object>>) restObjeto(
                "/obras/" + obraId + "/material-previsto?periodo=" + mes).get("items");
        return items.stream()
                .map(item -> List.of((String) item.get("partidaId"), (String) item.get("materialId"),
                        numero(item, "cantidadPrevista").stripTrailingZeros().toPlainString()))
                .toList();
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
