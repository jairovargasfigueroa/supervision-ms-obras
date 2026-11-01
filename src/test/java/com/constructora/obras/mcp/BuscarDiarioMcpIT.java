package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 501 · buscar_diario: rango por defecto (hoy − 14 a hoy, La Paz), orden, búsqueda sin distinguir mayúsculas con %, _
 * y la barra invertida como texto literal, rango máximo de 62 días, límite, total, truncado y texto recortado.
 */
@SuppressWarnings("unchecked")
class BuscarDiarioMcpIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Reloj reloj;

    private UUID obra;
    private LocalDate hoy;

    @BeforeEach
    void diario() throws Exception {
        hoy = reloj.hoy();
        obra = ObrasPrueba.registrar(mvc, "Diario MCP " + UUID.randomUUID(), Equipo.nuevo());
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        entrada(1, "LLUVIA_FUERTE", "Se suspendió el vaciado de la losa por LLUVIA fuerte");
        entrada(3, "SOLEADO", "Avance del 100% en el muro_norte, sin novedades");
        entrada(5, "NUBLADO", "Llegaron 10 bolsas de cemento; la caja oxn quedó revisada. " + "x".repeat(1500));
        entrada(6, "NUBLADO", "Ruta C:\\obra\\planos revisada con el residente");
        entrada(20, "LLUVIA_LEVE", "Entrada vieja fuera del rango por defecto");
    }

    @Test
    void porDefectoLasDeLosUltimos14DiasDeLaMasRecienteALaMasAntigua() throws Exception {
        Map<String, Object> salida = buscar(Map.of("obraId", obra.toString()));

        List<Map<String, Object>> entradas = (List<Map<String, Object>>) salida.get("entradas");
        assertThat(entradas).extracting(fila -> fila.get("fecha")).containsExactly(dia(1), dia(3), dia(5), dia(6));
        assertThat(entradas.getFirst()).containsEntry("clima", "LLUVIA_FUERTE").containsEntry("truncado", false);
        assertThat(salida).containsEntry("obraId", obra.toString()).containsEntry("total", 4)
                .containsEntry("truncado", false);
        Map<String, Object> larga = entradas.get(2);
        assertThat(larga).containsEntry("truncado", true);
        assertThat((String) larga.get("texto")).hasSize(HerramientasMcpObras.TEXTO_DIARIO_MAXIMO);
    }

    @Test
    void buscaSinDistinguirMayusculas() throws Exception {
        Map<String, Object> salida = buscar(Map.of("obraId", obra.toString(), "texto", "lluvia"));
        assertThat((List<Map<String, Object>>) salida.get("entradas")).extracting(fila -> fila.get("fecha"))
                .containsExactly(dia(1));
    }

    @Test
    void porcentajeGuionBajoYBarraSonTextoLiteral() throws Exception {
        // Sin escape, «0%» encontraría también «10 bolsas» y «o_n» encontraría «oxn»
        assertThat(fechas(Map.of("obraId", obra.toString(), "texto", "0%"))).containsExactly(dia(3));
        assertThat(fechas(Map.of("obraId", obra.toString(), "texto", "o_n"))).containsExactly(dia(3));
        assertThat(fechas(Map.of("obraId", obra.toString(), "texto", "\\obra"))).containsExactly(dia(6));
        assertThat(fechas(Map.of("obraId", obra.toString(), "texto", "%%"))).isEmpty();
    }

    @Test
    void conLimiteDiceElTotalYQueRecorto() throws Exception {
        Map<String, Object> salida = buscar(Map.of("obraId", obra.toString(), "limite", 1));
        assertThat((List<?>) salida.get("entradas")).hasSize(1);
        assertThat(salida).containsEntry("total", 4).containsEntry("truncado", true);
    }

    @Test
    void rangoDeHasta62Dias() throws Exception {
        assertThat(fechas(Map.of("obraId", obra.toString(), "desde", hoy.minusDays(62).toString(),
                "hasta", hoy.toString()))).contains(dia(20));
        assertThat(codigo(Map.of("obraId", obra.toString(), "desde", hoy.minusDays(63).toString(),
                "hasta", hoy.toString()))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("obraId", obra.toString(), "desde", hoy.minusDays(70).toString())))
                .isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("obraId", obra.toString(), "desde", hoy.toString(),
                "hasta", hoy.minusDays(1).toString()))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(Map.of("obraId", obra.toString(), "desde", "2027-02-30"))).isEqualTo("ARGUMENTO_INVALIDO");
    }

    @Test
    void obraInexistente() throws Exception {
        assertThat(codigo(Map.of("obraId", UUID.randomUUID().toString()))).isEqualTo("OBRA_NO_ENCONTRADA");
    }

    private void entrada(int diasAtras, String clima, String texto) {
        jdbc.update("""
                insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
                values (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), obra, hoy.minusDays(diasAtras), clima, texto,
                UUID.randomUUID(), Timestamp.from(Instant.now()));
    }

    private String dia(int diasAtras) {
        return hoy.minusDays(diasAtras).toString();
    }

    private Map<String, Object> buscar(Map<String, Object> argumentos) throws Exception {
        return McpPrueba.salida(McpPrueba.llamar(mvc, "buscar_diario", argumentos));
    }

    private List<Object> fechas(Map<String, Object> argumentos) throws Exception {
        return ((List<Map<String, Object>>) buscar(argumentos).get("entradas")).stream()
                .map(fila -> fila.get("fecha")).toList();
    }

    private Object codigo(Map<String, Object> argumentos) throws Exception {
        return McpPrueba.error(McpPrueba.llamar(mvc, "buscar_diario", argumentos)).get("codigo");
    }
}
