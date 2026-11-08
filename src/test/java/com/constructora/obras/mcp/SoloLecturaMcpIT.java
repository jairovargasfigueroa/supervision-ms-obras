package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.Esperar;
import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 501, criterio 3 · Prueba de solo lectura: después de llamar a todas las herramientas (con datos, con argumentos
 * inválidos y con obras que no existen), ninguna tabla cambió: misma cantidad de filas y misma suma de verificación.
 */
class SoloLecturaMcpIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Reloj reloj;

    @Test
    void ningunaTablaCambia() throws Exception {
        YearMonth mes = YearMonth.from(reloj.hoy());
        UUID obra = ObrasPrueba.registrarDosPartidas(mvc, "Solo lectura MCP " + UUID.randomUUID(), Equipo.nuevo());
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        UUID losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        PlanillasPrueba.insertar(jdbc, obra, 1, mes.minusMonths(1).toString(), "APROBADA",
                Map.of(losa, BigDecimal.ONE));
        UUID presentada = PlanillasPrueba.insertar(jdbc, obra, 2, mes.toString(), "PRESENTADA",
                Map.of(losa, BigDecimal.ONE));
        jdbc.update("update planilla set presentada_en = ? where id = ?", Timestamp.from(Instant.now()), presentada);
        jdbc.update("""
                insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
                values (?, ?, ?, 'LLUVIA_FUERTE', 'Se suspendió el trabajo por lluvia', ?, ?)""",
                UUID.randomUUID(), obra, reloj.hoy().minusDays(1), UUID.randomUUID(), Timestamp.from(Instant.now()));
        // El publicador del outbox marca los eventos del registro de la obra: se espera a que termine
        Esperar.hasta(Duration.ofSeconds(20), () -> jdbc.queryForObject(
                "select count(*) from outbox_evento where publicado_en is null", Integer.class) == 0);
        Map<String, String> antes = fotografia();

        String id = obra.toString();
        List<Map.Entry<String, Map<String, Object>>> llamadas = List.of(
                Map.entry("avance_obra", Map.of()),
                Map.entry("avance_obra", Map.of("obraId", id, "periodo", mes.toString())),
                Map.entry("avance_obra", Map.of("obraId", UUID.randomUUID().toString())),
                Map.entry("buscar_diario", Map.of("obraId", id, "texto", "lluvia")),
                Map.entry("buscar_diario", Map.of("obraId", "x'; delete from obra; --")),
                Map.entry("planillas_estado", Map.of()),
                Map.entry("planillas_estado", Map.of("obraId", id, "estados", List.of("APROBADA", "PRESENTADA"))),
                Map.entry("planillas_estado", Map.of("estados", List.of("x'); drop table planilla; --"))));
        for (Map.Entry<String, Map<String, Object>> llamada : llamadas) {
            McpPrueba.llamar(mvc, llamada.getKey(), llamada.getValue());
        }

        assertThat(fotografia()).isEqualTo(antes);
    }

    /** Por tabla del esquema (menos el historial de Flyway): cantidad de filas y md5 de todas sus filas ordenadas. */
    private Map<String, String> fotografia() {
        Map<String, String> tablas = new TreeMap<>();
        for (String tabla : jdbc.queryForList("""
                select tablename from pg_tables
                where schemaname = 'public' and tablename <> 'flyway_schema_history'""", String.class)) {
            tablas.put(tabla, jdbc.queryForObject("select count(*) || ':' || coalesce(md5(string_agg(t::text, '|'"
                    + " order by t::text)), '') from \"" + tabla + "\" t", String.class));
        }
        assertThat(tablas).containsKeys("obra", "partida", "planilla", "linea_planilla", "entrada_diario");
        return tablas;
    }
}
