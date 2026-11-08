package com.constructora.obras.avance.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.AlmacenArchivosEnMemoria;
import com.constructora.obras.soporte.AlmacenArchivosEnMemoria.Firma;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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
 * 005 · Pruebas de la spec (diseño, «Pruebas»): URL firmada con la clave esperada, registro idempotente, partida de
 * otra obra, maestro no asignado, almacenero asignado, cliente solo ve publicadas; y la lista paginada.
 */
class FotoAvanceIT extends PruebaIntegracion {

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
        obraId = ObrasPrueba.registrar(mvc, "Obra con fotos", equipo);
        partidaId = primeraPartida(obraId);
        maestro = TokensPrueba.desde("app-obra", equipo.maestro().toString(), "maestro");
        residente = TokensPrueba.valido(equipo.residente().toString(), "residente");
    }

    @AfterEach
    void reiniciarAlmacen() {
        almacen.reiniciar();
    }

    @Test
    void urlDeSubidaFirmadaConLaClaveEsperada() throws Exception {
        UUID fotoId = UUID.randomUUID();
        String clave = "fotos/" + obraId + "/" + fotoId + ".jpg";

        pedirUrl(obraId, fotoId, maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claveArchivo").value(clave))
                .andExpect(jsonPath("$.url").value(startsWith(AlmacenArchivosEnMemoria.BASE + clave)))
                .andExpect(jsonPath("$.expiraEn").isString());

        assertThat(almacen.firmas()).singleElement()
                .isEqualTo(new Firma(clave, "PUT", "image/jpeg", Duration.ofMinutes(10)));
    }

    @Test
    void registroIdempotente() throws Exception {
        UUID fotoId = UUID.randomUUID();
        String cuerpo = cuerpoFoto(fotoId, partidaId, clave(obraId, fotoId), "2026-11-15T15:20:00Z");

        String creada = registrar(obraId, cuerpo, maestro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fotoId.toString()))
                .andExpect(jsonPath("$.partidaId").value(partidaId.toString()))
                .andExpect(jsonPath("$.registradoPor").value(equipo.maestro().toString()))
                .andExpect(jsonPath("$.registradoEn").value("2026-11-15T15:20:00Z"))
                .andExpect(jsonPath("$.publicadaCliente").value(false))
                .andReturn().getResponse().getContentAsString();

        String repetida = registrar(obraId, cuerpo, maestro)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(repetida).isEqualTo(creada);
        assertThat(jdbc.queryForObject("select count(*) from foto_avance where id = ?", Integer.class, fotoId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select clave_archivo from foto_avance where id = ?", String.class, fotoId))
                .isEqualTo(clave(obraId, fotoId));
    }

    @Test
    void elResidenteAsignadoTambienRegistraPorLaApi() throws Exception {
        UUID fotoId = UUID.randomUUID();
        registrar(obraId, cuerpoFoto(fotoId, partidaId, clave(obraId, fotoId), "2026-11-15T15:20:00Z"), residente)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoPor").value(equipo.residente().toString()));
    }

    @Test
    void partidaDeOtraObra() throws Exception {
        UUID otraObra = ObrasPrueba.registrar(mvc, "Otra obra", Equipo.nuevo());
        UUID partidaAjena = primeraPartida(otraObra);
        UUID fotoId = UUID.randomUUID();

        registrar(obraId, cuerpoFoto(fotoId, partidaAjena, clave(obraId, fotoId), "2026-11-15T15:20:00Z"), maestro)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PARTIDA_NO_ES_DE_LA_OBRA"))
                .andExpect(jsonPath("$.errores[0].campo").value("partidaId"));
        assertThat(jdbc.queryForObject("select count(*) from foto_avance where id = ?", Integer.class, fotoId))
                .isZero();
    }

    @Test
    void claveQueNoEsLaDeEsaFoto() throws Exception {
        UUID fotoId = UUID.randomUUID();

        registrar(obraId, cuerpoFoto(fotoId, partidaId, clave(obraId, UUID.randomUUID()), "2026-11-15T15:20:00Z"),
                maestro)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CLAVE_ARCHIVO_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("claveArchivo"));
    }

    @Test
    void maestroNoAsignadoRecibeObraNoEncontrada() throws Exception {
        String otroMaestro = TokensPrueba.desde("app-obra", UUID.randomUUID().toString(), "maestro");
        UUID fotoId = UUID.randomUUID();

        pedirUrl(obraId, fotoId, otroMaestro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        registrar(obraId, cuerpoFoto(fotoId, partidaId, clave(obraId, fotoId), "2026-11-15T15:20:00Z"), otroMaestro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        listar("/obras/" + obraId + "/fotos", otroMaestro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        pedirUrl(UUID.randomUUID(), fotoId, maestro).andExpect(status().isNotFound());
        listar("/obras/no-es-uuid/fotos", maestro)
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
    }

    @Test
    void almaceneroAsignadoRecibeSinPermisoAntesQueElCuerpo() throws Exception {
        String almacenero = TokensPrueba.desde("app-obra", equipo.almacenero().toString(), "almacenero");

        pedirUrl(obraId, UUID.randomUUID(), almacenero)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        registrar(obraId, "{}", almacenero)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // El cliente y el admin tampoco registran
        registrar(obraId, "{}", TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                .andExpect(status().isForbidden());
        registrar(obraId, "{}", TokensPrueba.valido(ObrasPrueba.ADMIN, "admin")).andExpect(status().isForbidden());
    }

    @Test
    void cuerpoIncompletoEsDatosInvalidos() throws Exception {
        registrar(obraId, "{\"id\":\"" + UUID.randomUUID() + "\"}", maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(post("/obras/" + obraId + "/fotos/url-subida").header("Authorization", "Bearer " + maestro)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fotoId\":\"no-es-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void sinAlmacenamientoLaUrlDeSubidaResponde503YLaListaVaciaResponde() throws Exception {
        almacen.noDisponible();

        pedirUrl(obraId, UUID.randomUUID(), maestro)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        listar("/obras/" + obraId + "/fotos", residente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));

        // El registro no necesita el almacenamiento; la lista con fotos sí
        UUID fotoId = UUID.randomUUID();
        registrar(obraId, cuerpoFoto(fotoId, partidaId, clave(obraId, fotoId), "2026-11-15T15:20:00Z"), maestro)
                .andExpect(status().isCreated());
        listar("/obras/" + obraId + "/fotos", residente)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    void listaDeLaMasRecienteALaMasAntiguaConUrlDeDescarga() throws Exception {
        UUID vieja = registrarFoto("2026-11-10T12:00:00Z");
        UUID nueva = registrarFoto("2026-11-15T12:00:00Z");
        UUID media = registrarFoto("2026-11-12T12:00:00Z");

        listar("/obras/" + obraId + "/fotos", residente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.items[0].id").value(nueva.toString()))
                .andExpect(jsonPath("$.items[1].id").value(media.toString()))
                .andExpect(jsonPath("$.items[2].id").value(vieja.toString()))
                .andExpect(jsonPath("$.items[0].partidaId").value(partidaId.toString()))
                .andExpect(jsonPath("$.items[0].registradoPor").value(equipo.maestro().toString()))
                .andExpect(jsonPath("$.items[0].registradoEn").value("2026-11-15T12:00:00Z"))
                .andExpect(jsonPath("$.items[0].publicadaCliente").value(false))
                .andExpect(jsonPath("$.items[0].urlDescarga")
                        .value(AlmacenArchivosEnMemoria.BASE + clave(obraId, nueva) + "?metodo=GET&vigencia=1800"));

        // Página 2 de tamaño 1, en orden ascendente
        listar("/obras/" + obraId + "/fotos?pagina=2&tamano=1&orden=registradoEn&direccion=asc", residente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamano").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(media.toString()));

        // Filtro por partida
        listar("/obras/" + obraId + "/fotos?partidaId=" + partidaId, residente)
                .andExpect(jsonPath("$.total").value(3));
        listar("/obras/" + obraId + "/fotos?partidaId=" + UUID.randomUUID(), residente)
                .andExpect(jsonPath("$.total").value(0));

        // Los que ven la obra
        listar("/obras/" + obraId + "/fotos", maestro).andExpect(jsonPath("$.total").value(3));
        listar("/obras/" + obraId + "/fotos",
                TokensPrueba.desde("app-obra", equipo.almacenero().toString(), "almacenero"))
                .andExpect(jsonPath("$.total").value(3));
        listar("/obras/" + obraId + "/fotos", TokensPrueba.valido("u-gerente", "gerente"))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void clienteSoloVePublicadas() throws Exception {
        registrarFoto("2026-11-10T12:00:00Z");
        UUID publicada = registrarFoto("2026-11-11T12:00:00Z");
        // Lo hace la 007 al presentar la planilla
        jdbc.update("update foto_avance set publicada_cliente = true where id = ?", publicada);
        String cliente = TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente");

        listar("/obras/" + obraId + "/fotos", cliente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publicada.toString()))
                .andExpect(jsonPath("$.items[0].publicadaCliente").value(true));
        listar("/obras/" + obraId + "/fotos", residente).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void paginacionInvalida() throws Exception {
        listar("/obras/" + obraId + "/fotos?tamano=101", residente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAGINACION_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("tamano"));
        listar("/obras/" + obraId + "/fotos?orden=nombre", residente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAGINACION_INVALIDA"));
        listar("/obras/" + obraId + "/fotos?partidaId=no-es-uuid", residente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    private UUID registrarFoto(String tomadaEn) throws Exception {
        UUID fotoId = UUID.randomUUID();
        registrar(obraId, cuerpoFoto(fotoId, partidaId, clave(obraId, fotoId), tomadaEn), maestro)
                .andExpect(status().isCreated());
        return fotoId;
    }

    private UUID primeraPartida(UUID obra) throws Exception {
        String detalle = mvc.perform(get("/obras/" + obra)
                        .header("Authorization", "Bearer " + TokensPrueba.valido(ObrasPrueba.ADMIN, "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(detalle, "$.partidas[0].id"));
    }

    private ResultActions pedirUrl(UUID obra, UUID fotoId, String token) throws Exception {
        return mvc.perform(post("/obras/" + obra + "/fotos/url-subida").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fotoId\":\"" + fotoId + "\"}"));
    }

    private ResultActions registrar(UUID obra, String cuerpo, String token) throws Exception {
        return mvc.perform(post("/obras/" + obra + "/fotos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions listar(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private static String clave(UUID obra, UUID fotoId) {
        return "fotos/" + obra + "/" + fotoId + ".jpg";
    }

    private static String cuerpoFoto(UUID id, UUID partida, String clave, String tomadaEn) {
        return """
                { "id": "%s", "partidaId": "%s", "claveArchivo": "%s", "tomadaEn": "%s" }"""
                .formatted(id, partida, clave, tomadaEn);
    }
}
