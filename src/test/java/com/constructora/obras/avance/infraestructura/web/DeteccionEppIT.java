package com.constructora.obras.avance.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.AlmacenArchivosEnMemoria;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 301 · Pruebas de la spec (diseño, «Pruebas», Obras): registro con y sin deteccion; sinCasco > personas (foto
 * registrada sin detección y aviso DETECCION_DESCARTADA); registro repetido no cambia la detección; el cliente recibe
 * deteccion: null.
 */
class DeteccionEppIT extends PruebaIntegracion {

    private static final String DETECCION = """
            { "personas": 4, "sinCasco": 1, "sinChaleco": 2, "versionModelo": "epp-1.0.0" }""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AlmacenArchivosEnMemoria almacen;
    @Autowired
    private JdbcTemplate jdbc;

    private Equipo equipo;
    private UUID obraId;
    private UUID partidaId;
    private String maestro;
    private String residente;

    @BeforeEach
    void registrarObra() throws Exception {
        almacen.reiniciar();
        equipo = Equipo.nuevo();
        obraId = ObrasPrueba.registrar(mvc, "Obra con detección de EPP", equipo);
        partidaId = primeraPartida(obraId);
        maestro = TokensPrueba.desde("app-obra", equipo.maestro().toString(), "maestro");
        residente = TokensPrueba.valido(equipo.residente().toString(), "residente");
    }

    @AfterEach
    void reiniciarAlmacen() {
        almacen.reiniciar();
    }

    @Test
    void registraConDeteccionYLaDevuelveEnLaLista() throws Exception {
        UUID fotoId = UUID.randomUUID();

        registrar(fotoId, DETECCION)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deteccion.personas").value(4))
                .andExpect(jsonPath("$.deteccion.sinCasco").value(1))
                .andExpect(jsonPath("$.deteccion.sinChaleco").value(2))
                .andExpect(jsonPath("$.deteccion.versionModelo").value("epp-1.0.0"))
                .andExpect(jsonPath("$.avisos").isEmpty());

        assertThat(columnas(fotoId)).containsAllEntriesOf(Map.of("personas_detectadas", 4, "sin_casco", 1,
                "sin_chaleco", 2, "version_modelo_epp", "epp-1.0.0"));
        listar(residente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fotoId.toString()))
                .andExpect(jsonPath("$.items[0].deteccion.personas").value(4))
                .andExpect(jsonPath("$.items[0].deteccion.sinCasco").value(1))
                .andExpect(jsonPath("$.items[0].deteccion.sinChaleco").value(2))
                .andExpect(jsonPath("$.items[0].deteccion.versionModelo").value("epp-1.0.0"));
    }

    @Test
    void sinDeteccionLaFotoQuedaSinAnalisis() throws Exception {
        UUID sinCampo = UUID.randomUUID();
        UUID conNull = UUID.randomUUID();

        registrar(sinCampo, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deteccion").value(nullValue()))
                .andExpect(jsonPath("$.avisos").isEmpty());
        registrar(conNull, "null")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deteccion").value(nullValue()))
                .andExpect(jsonPath("$.avisos").isEmpty());

        assertThat(columnas(sinCampo)).containsOnly(Map.entry("personas_detectadas", "null"),
                Map.entry("sin_casco", "null"), Map.entry("sin_chaleco", "null"),
                Map.entry("version_modelo_epp", "null"));
        listar(residente)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].deteccion").value(nullValue()))
                .andExpect(jsonPath("$.items[1].deteccion").value(nullValue()));
    }

    @Test
    void sinCascoMayorQuePersonasSeRegistraSinDeteccionYConAviso() throws Exception {
        UUID fotoId = UUID.randomUUID();

        registrar(fotoId, """
                { "personas": 4, "sinCasco": 5, "sinChaleco": 2, "versionModelo": "epp-1.0.0" }""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fotoId.toString()))
                .andExpect(jsonPath("$.deteccion").value(nullValue()))
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]").value("DETECCION_DESCARTADA"));

        assertThat(jdbc.queryForObject("select count(*) from foto_avance where id = ?", Integer.class, fotoId))
                .isEqualTo(1);
        assertThat(columnas(fotoId)).containsEntry("personas_detectadas", "null").containsEntry("sin_casco", "null");
    }

    @Test
    void unaDeteccionMalFormadaNoEsUn400() throws Exception {
        String[] malFormadas = {
            "{ \"personas\": -1, \"sinCasco\": 0, \"sinChaleco\": 0, \"versionModelo\": \"epp-1.0.0\" }",
            "{ \"personas\": 4, \"sinCasco\": 1, \"sinChaleco\": 2, \"versionModelo\": \"\" }",
            "{ \"personas\": 4, \"sinCasco\": 1, \"sinChaleco\": 2, \"versionModelo\": \"" + "e".repeat(41) + "\" }",
            "{ \"personas\": 4, \"sinCasco\": 1, \"sinChaleco\": 2 }",
            "{ \"personas\": 4.5, \"sinCasco\": 1, \"sinChaleco\": 2, \"versionModelo\": \"epp-1.0.0\" }",
            "{ \"personas\": \"4\", \"sinCasco\": 1, \"sinChaleco\": 2, \"versionModelo\": \"epp-1.0.0\" }",
            "{ \"personas\": 4, \"sinCasco\": 1, \"sinChaleco\": 2, \"versionModelo\": 1 }",
            "\"epp\"",
            "[4, 1, 2]"
        };
        for (String deteccion : malFormadas) {
            registrar(UUID.randomUUID(), deteccion)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.deteccion").value(nullValue()))
                    .andExpect(jsonPath("$.avisos[0]").value("DETECCION_DESCARTADA"));
        }
    }

    @Test
    void elRegistroRepetidoNoCambiaLaDeteccion() throws Exception {
        UUID fotoId = UUID.randomUUID();
        String creada = registrar(fotoId, DETECCION)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String repetida = registrar(fotoId, DETECCION).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        registrar(fotoId, """
                { "personas": 2, "sinCasco": 0, "sinChaleco": 0, "versionModelo": "epp-2.0.0" }""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deteccion.personas").value(4))
                .andExpect(jsonPath("$.deteccion.versionModelo").value("epp-1.0.0"))
                .andExpect(jsonPath("$.avisos").isEmpty());
        registrar(fotoId, "{ \"personas\": 1, \"sinCasco\": 9, \"sinChaleco\": 0, \"versionModelo\": \"x\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deteccion.sinCasco").value(1))
                .andExpect(jsonPath("$.avisos").isEmpty());
        registrar(fotoId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deteccion.personas").value(4));

        assertThat(repetida).isEqualTo(creada);
        assertThat(columnas(fotoId)).containsAllEntriesOf(Map.of("personas_detectadas", 4, "sin_casco", 1,
                "sin_chaleco", 2, "version_modelo_epp", "epp-1.0.0"));
    }

    @Test
    void elClienteRecibeDeteccionNull() throws Exception {
        UUID fotoId = UUID.randomUUID();
        registrar(fotoId, DETECCION).andExpect(status().isCreated());
        // Lo hace la 007 al presentar la planilla
        jdbc.update("update foto_avance set publicada_cliente = true where id = ?", fotoId);

        listar(TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(fotoId.toString()))
                .andExpect(jsonPath("$.items[0].deteccion").value(nullValue()));
        // Los roles internos que ven la obra sí la reciben
        listar(maestro).andExpect(jsonPath("$.items[0].deteccion.personas").value(4));
        listar(TokensPrueba.valido("u-gerente", "gerente"))
                .andExpect(jsonPath("$.items[0].deteccion.personas").value(4));
    }

    /** Las cuatro columnas de la detección; los null como el texto "null" para comparar con Map. */
    private Map<String, Object> columnas(UUID fotoId) {
        Map<String, Object> fila = jdbc.queryForMap("""
                select personas_detectadas, sin_casco, sin_chaleco, version_modelo_epp
                from foto_avance where id = ?""", fotoId);
        fila.replaceAll((columna, valor) -> valor == null ? "null" : valor);
        return fila;
    }

    /** deteccion: el JSON del campo, o null para no enviarlo. */
    private ResultActions registrar(UUID fotoId, String deteccion) throws Exception {
        String cuerpo = """
                { "id": "%s", "partidaId": "%s", "claveArchivo": "fotos/%s/%s.jpg", "tomadaEn": "2026-11-15T15:20:00Z"%s }"""
                .formatted(fotoId, partidaId, obraId, fotoId, deteccion == null ? "" : ", \"deteccion\": " + deteccion);
        return mvc.perform(post("/obras/" + obraId + "/fotos").header("Authorization", "Bearer " + maestro)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions listar(String token) throws Exception {
        return mvc.perform(get("/obras/" + obraId + "/fotos").header("Authorization", "Bearer " + token));
    }

    private UUID primeraPartida(UUID obra) throws Exception {
        String detalle = mvc.perform(get("/obras/" + obra)
                        .header("Authorization", "Bearer " + TokensPrueba.valido(ObrasPrueba.ADMIN, "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(detalle, "$.partidas[0].id"));
    }
}
