package com.constructora.obras.obra.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.Esperar;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tarea 8: el flujo de la 002 de punta a punta dentro de Obras. POST /obras → ObraRegistrada sale por el outbox →
 * (Finanzas, simulado) ContratoRegistrado → la obra queda EN_EJECUCION sin que el usuario haga nada más.
 */
class FlujoRegistrarObraIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin admin;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    /** Hace de cola de Finanzas: recibe lo que Obras publica. */
    private Queue finanzas;

    @BeforeEach
    void escucharComoFinanzas() {
        finanzas = new Queue("prueba." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(finanzas);
        admin.declareBinding(BindingBuilder.bind(finanzas).to(new TopicExchange("plataforma"))
                .with("evento.obras.ObraRegistrada"));
    }

    @AfterEach
    void borrarCola() {
        admin.deleteQueue(finanzas.getName());
    }

    @Test
    void registrarYRecibirElContratoDejaLaObraEnEjecucion() throws Exception {
        Equipo equipo = Equipo.nuevo();
        UUID obraId = ObrasPrueba.registrar(mvc, "Torre Flujo", equipo);
        String residente = TokensPrueba.valido(equipo.residente().toString(), "residente");

        // Criterio 7: aparece en la lista sin contrato
        mvc.perform(get("/obras").header("Authorization", "Bearer " + residente))
                .andExpect(jsonPath("$[0].estado").value("SIN_CONTRATO"));

        JsonNode sobre = recibirObraRegistrada(obraId);
        assertThat(sobre.get("datos").get("asignaciones").get("almaceneroId").asString())
                .isEqualTo(equipo.almacenero().toString());
        assertThat(sobre.get("datos").get("contrato").get("plazoDias").asInt()).isEqualTo(100);

        // Finanzas responde con el contrato creado
        UUID contratoId = UUID.randomUUID();
        UUID mensajeId = UUID.randomUUID();
        rabbit.send("plataforma", "evento.finanzas.ContratoRegistrado", MessageBuilder.withBody("""
                {"id": "%s", "tipo": "ContratoRegistrado", "version": 1, "ocurridoEn": "%s", "origen": "finanzas",
                 "correlacionId": "%s", "datos": {"obraId": "%s", "contratoId": "%s"}}"""
                .formatted(mensajeId, Instant.now(), sobre.get("correlacionId").asString(), obraId, contratoId)
                .getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(mensajeId.toString()).build());

        // Criterio 8: pasa a EN_EJECUCION en pocos segundos
        Esperar.hasta(Duration.ofSeconds(10), () -> "EN_EJECUCION".equals(jdbc.queryForObject(
                "select estado from obra where id = ?", String.class, obraId)));
        mvc.perform(get("/obras/" + obraId).header("Authorization", "Bearer " + residente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.contrato.contratoId").value(contratoId.toString()));
        mvc.perform(get("/obras").header("Authorization", "Bearer " + residente))
                .andExpect(jsonPath("$[*].estado", hasItem("EN_EJECUCION")));
    }

    @Test
    void unaObraRechazadaNoDejaNadaEnLaBaseNiEnElOutbox() throws Exception {
        int obras = contar("obra");
        int outbox = contar("outbox_evento");

        String cuerpo = ObrasPrueba.cuerpo("Obra que no cuadra", Equipo.nuevo()).replace("\"monto\": 1000",
                "\"monto\": 999");
        mvc.perform(post("/obras").header("Authorization", "Bearer " + TokensPrueba.valido(ObrasPrueba.ADMIN, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_COINCIDE"));

        assertThat(contar("obra")).isEqualTo(obras);
        assertThat(contar("outbox_evento")).isEqualTo(outbox);
    }

    @Test
    void cadaObraRegistradaQuedaEnElOutboxYSeMarcaPublicada() throws Exception {
        UUID obraId = ObrasPrueba.registrar(mvc, "Obra outbox", Equipo.nuevo());

        assertThat(jdbc.queryForObject("""
                select count(*) from outbox_evento
                where tipo = 'ObraRegistrada' and payload -> 'datos' ->> 'obraId' = ?""", Integer.class,
                obraId.toString())).isEqualTo(1);
        Esperar.hasta(Duration.ofSeconds(5), () -> jdbc.queryForObject("""
                select publicado_en is not null from outbox_evento
                where payload -> 'datos' ->> 'obraId' = ?""", Boolean.class, obraId.toString()));
        recibirObraRegistrada(obraId);
    }

    private JsonNode recibirObraRegistrada(UUID obraId) {
        Instant fin = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive(finanzas.getName(), 500);
            if (mensaje != null) {
                JsonNode sobre = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
                if (obraId.toString().equals(sobre.get("datos").get("obraId").asString())) {
                    return sobre;
                }
            }
        }
        throw new AssertionError("Finanzas no recibió ObraRegistrada de la obra " + obraId);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Integer.class);
    }
}
