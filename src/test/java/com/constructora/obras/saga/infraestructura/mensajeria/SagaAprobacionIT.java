package com.constructora.obras.saga.infraestructura.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.infraestructura.mensajeria.ConfiguracionRabbit;
import com.constructora.obras.saga.infraestructura.programacion.VencimientoSagaTarea;
import com.constructora.obras.soporte.Esperar;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 008 · La saga de punta a punta dentro de Obras, con RabbitMQ real: Finanzas y Materiales los simula la prueba (lee
 * los comandos de colas propias y publica las respuestas en el exchange plataforma). Camino feliz hasta APROBADA con
 * el número de cobro y el avance de la obra, un fallo con su compensación y respuestas que van a fallidos.
 */
class SagaAprobacionIT extends PruebaIntegracion {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin admin;
    @Autowired
    private JsonMapper json;
    @Autowired
    private VencimientoSagaTarea vencimiento;

    private Equipo equipo;
    private UUID obra;
    private UUID losa;
    private Queue finanzas;
    private Queue materiales;
    private Queue eventosObras;

    @BeforeEach
    void obraYColasSimuladas() throws Exception {
        equipo = Equipo.nuevo();
        obra = ObrasPrueba.registrar(mvc, "Edificio Saga", equipo);
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        finanzas = cola("comando.finanzas.#");
        materiales = cola("comando.materiales.#");
        eventosObras = cola("evento.obras.#");
    }

    @AfterEach
    void borrarColas() {
        admin.deleteQueue(finanzas.getName());
        admin.deleteQueue(materiales.getName());
        admin.deleteQueue(eventosObras.getName());
    }

    @Test
    void caminoFelizHastaAprobadaConElCobroYElAvanceDeLaObra() throws Exception {
        UUID planilla = aprobarPresentada(new BigDecimal("4"));
        JsonNode reservar = comando(finanzas, planilla, "ReservarSaldo");
        UUID saga = UUID.fromString(reservar.get("correlacionId").asString());
        UUID reservaId = UUID.randomUUID();

        responder("evento.finanzas.SaldoReservado", saga, Map.of("planillaId", planilla, "reservaId", reservaId));
        JsonNode cerrar = comando(materiales, planilla, "CerrarConsumos");
        assertThat(cerrar.get("correlacionId").asString()).isEqualTo(saga.toString());
        assertThat(cerrar.get("datos").get("obraId").asString()).isEqualTo(obra.toString());
        assertThat(cerrar.get("datos").get("periodo").asString()).isEqualTo("2026-10");
        assertThat(paso(saga)).isEqualTo("CERRAR_CONSUMOS");

        responder("evento.materiales.ConsumosCerrados", saga,
                Map.of("planillaId", planilla, "obraId", obra, "periodo", "2026-10"));
        JsonNode emitir = comando(finanzas, planilla, "EmitirCobro");
        assertThat(emitir.get("datos").get("reservaId").asString()).isEqualTo(reservaId.toString());
        assertThat(emitir.get("datos").get("monto").decimalValue()).isEqualByComparingTo("332.00");

        UUID cobroId = UUID.randomUUID();
        responder("evento.finanzas.CobroEmitido", saga, Map.of("planillaId", planilla, "cobroId", cobroId,
                "numero", "COB-2026-0001", "venceEn", "2026-11-28"));
        Esperar.hasta(ESPERA, () -> "APROBADA".equals(estadoPlanilla(planilla)));

        assertThat(estadoSaga(saga)).isEqualTo("COMPLETADA");
        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + residente()))
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.cobroId").value(cobroId.toString()))
                .andExpect(jsonPath("$.cobroNumero").value("COB-2026-0001"));
        // Criterio 13: el avance de la obra ya incluye la planilla (4 de 10 m2)
        mvc.perform(get("/obras/{id}", obra).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.partidas[0].ejecutadaAcumulada").value(4))
                .andExpect(jsonPath("$.avancePct").value(40.0));
        JsonNode aprobada = evento(planilla, "PlanillaAprobada");
        assertThat(aprobada.get("correlacionId").asString()).isEqualTo(saga.toString());
        JsonNode datos = aprobada.get("datos");
        assertThat(datos.propertyNames()).containsExactlyInAnyOrder("planillaId", "obraId", "periodo", "neto",
                "cobroId", "lineas");
        assertThat(datos.get("periodo").asString()).isEqualTo("2026-10");
        assertThat(datos.get("neto").decimalValue()).isEqualByComparingTo("332.00");
        assertThat(datos.get("cobroId").asString()).isEqualTo(cobroId.toString());
        assertThat(datos.get("lineas").get(0).get("partidaId").asString()).isEqualTo(losa.toString());
        assertThat(datos.get("lineas").get(0).get("cantidadPeriodo").decimalValue()).isEqualByComparingTo("4");
        assertThat(datos.get("lineas").get(0).get("monto").decimalValue()).isEqualByComparingTo("400.00");
    }

    @Test
    void siFallaElCierreSeLiberaLaReservaYQuedaObservada() throws Exception {
        UUID planilla = aprobarPresentada(BigDecimal.ONE);
        UUID saga = UUID.fromString(comando(finanzas, planilla, "ReservarSaldo").get("correlacionId").asString());
        UUID reservaId = UUID.randomUUID();
        responder("evento.finanzas.SaldoReservado", saga, Map.of("planillaId", planilla, "reservaId", reservaId));
        comando(materiales, planilla, "CerrarConsumos");

        String motivo = "Los consumos del periodo 2026-10 ya fueron cerrados por otra planilla";
        responder("evento.materiales.CierreFallido", saga, Map.of("planillaId", planilla, "motivo", motivo));

        JsonNode liberar = comando(finanzas, planilla, "LiberarReserva");
        assertThat(liberar.get("datos").get("reservaId").asString()).isEqualTo(reservaId.toString());
        assertThat(estadoSaga(saga)).isEqualTo("COMPENSADA");
        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.estado").value("OBSERVADA"))
                .andExpect(jsonPath("$.observacion").value(motivo));
        JsonNode observada = evento(planilla, "PlanillaObservada");
        assertThat(observada.get("datos").get("origen").asString()).isEqualTo("SAGA");
        assertThat(observada.get("datos").get("motivo").asString()).isEqualTo(motivo);
    }

    @Test
    void sinCorrelacionIdOConUnaSagaQueNoExisteVaAFallidos() throws Exception {
        UUID planilla = aprobarPresentada(BigDecimal.ONE);
        UUID saga = UUID.fromString(comando(finanzas, planilla, "ReservarSaldo").get("correlacionId").asString());

        UUID sinCorrelacion = responder("evento.finanzas.SaldoReservado", null,
                Map.of("planillaId", planilla, "reservaId", UUID.randomUUID()));
        assertThat(fallido(sinCorrelacion).get("x-error").toString()).contains("correlacionId");
        UUID sagaInexistente = responder("evento.finanzas.SaldoReservado", UUID.randomUUID(),
                Map.of("planillaId", planilla, "reservaId", UUID.randomUUID()));
        assertThat(fallido(sagaInexistente).get("x-error").toString()).contains("No hay una saga");
        UUID otraPlanilla = responder("evento.finanzas.SaldoReservado", saga,
                Map.of("planillaId", UUID.randomUUID(), "reservaId", UUID.randomUUID()));
        assertThat(fallido(otraPlanilla).get("x-error").toString()).contains("no es de la planilla");
        UUID incompleto = responder("evento.finanzas.CobroEmitido", saga, Map.of("planillaId", planilla));
        assertThat(((Number) fallido(incompleto).get("x-intentos")).intValue()).isEqualTo(1);

        assertThat(estadoSaga(saga)).isEqualTo("EN_CURSO");
        assertThat(paso(saga)).isEqualTo("RESERVAR_SALDO");
        assertThat(estadoPlanilla(planilla)).isEqualTo("APROBANDO");
    }

    @Test
    void unPasoSinRespuestaVenceSeCompensaYLaRespuestaTardiaSeDeshace() throws Exception {
        UUID planilla = aprobarPresentada(BigDecimal.ONE);
        UUID saga = UUID.fromString(comando(finanzas, planilla, "ReservarSaldo").get("correlacionId").asString());
        UUID reservaId = UUID.randomUUID();
        responder("evento.finanzas.SaldoReservado", saga, Map.of("planillaId", planilla, "reservaId", reservaId));
        comando(materiales, planilla, "CerrarConsumos");

        // Pasaron los 60 s del paso 3 sin respuesta de Materiales
        vencido(saga);
        vencimiento.vencerPasos();

        assertThat(comando(finanzas, planilla, "LiberarReserva").get("datos").get("reservaId").asString())
                .isEqualTo(reservaId.toString());
        assertThat(estadoSaga(saga)).isEqualTo("COMPENSADA");
        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.estado").value("OBSERVADA"))
                .andExpect(jsonPath("$.observacion").value("El paso 3 no respondió a tiempo"));

        // Materiales responde tarde: se reabren los consumos y la planilla sigue OBSERVADA
        responder("evento.materiales.ConsumosCerrados", saga,
                Map.of("planillaId", planilla, "obraId", obra, "periodo", "2026-10"));
        JsonNode reabrir = comando(materiales, planilla, "ReabrirConsumos");
        assertThat(reabrir.get("correlacionId").asString()).isEqualTo(saga.toString());
        assertThat(reabrir.get("datos").get("periodo").asString()).isEqualTo("2026-10");
        assertThat(estadoPlanilla(planilla)).isEqualTo("OBSERVADA");
        assertThat(estadoSaga(saga)).isEqualTo("COMPENSADA");
    }

    @Test
    void unaReservaQueLlegaDespuesDeVencerElPaso2SeLibera() throws Exception {
        UUID planilla = aprobarPresentada(BigDecimal.ONE);
        UUID saga = UUID.fromString(comando(finanzas, planilla, "ReservarSaldo").get("correlacionId").asString());
        vencido(saga);

        vencimiento.vencerPasos();
        Esperar.hasta(ESPERA, () -> "OBSERVADA".equals(estadoPlanilla(planilla)));
        assertThat(jdbc.queryForObject("select observacion from planilla where id = ?", String.class, planilla))
                .isEqualTo("El paso 2 no respondió a tiempo");

        UUID tardia = UUID.randomUUID();
        responder("evento.finanzas.SaldoReservado", saga, Map.of("planillaId", planilla, "reservaId", tardia));
        assertThat(comando(finanzas, planilla, "LiberarReserva").get("datos").get("reservaId").asString())
                .isEqualTo(tardia.toString());
        assertThat(estadoSaga(saga)).isEqualTo("COMPENSADA");
    }

    // --- ayudas ---

    private Queue cola(String clave) {
        Queue cola = new Queue("prueba.saga." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(cola);
        admin.declareBinding(BindingBuilder.bind(cola).to(new TopicExchange(ConfiguracionRabbit.EXCHANGE)).with(clave));
        return cola;
    }

    /** Como si hubieran pasado los 60 s del paso actual (con la hora de la aplicación, no la de la base). */
    private void vencido(UUID saga) {
        jdbc.update("update saga_aprobacion set vence_en = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), saga);
    }

    /** Planilla PRESENTADA del periodo 2026-10 con esa cantidad de losa, aprobada por el cliente (202). */
    private UUID aprobarPresentada(BigDecimal cantidad) throws Exception {
        UUID id = PlanillasPrueba.insertar(jdbc, obra, 1, "2026-10", "PRESENTADA", Map.of(losa, cantidad));
        BigDecimal subtotal = cantidad.multiply(BigDecimal.valueOf(100));
        BigDecimal anticipo = subtotal.multiply(new BigDecimal("0.10"));
        BigDecimal retencion = subtotal.multiply(new BigDecimal("0.07"));
        jdbc.update("update planilla set subtotal = ?, descuento_anticipo = ?, retencion = ?, neto = ? where id = ?",
                subtotal, anticipo, retencion, subtotal.subtract(anticipo).subtract(retencion), id);
        mvc.perform(post("/planillas/{id}/aprobar", id).header("Authorization", "Bearer " + cliente()))
                .andExpect(status().isAccepted());
        return id;
    }

    /** Publica la respuesta con el sobre común, como lo haría Finanzas o Materiales; devuelve el id del mensaje. */
    private UUID responder(String clave, UUID correlacionId, Map<String, Object> datos) {
        UUID id = UUID.randomUUID();
        String origen = clave.split("\\.")[1];
        String tipo = clave.substring(clave.lastIndexOf('.') + 1);
        String cuerpo = """
                {"id": "%s", "tipo": "%s", "version": 1, "ocurridoEn": "%s", "origen": "%s",
                 "correlacionId": %s, "datos": %s}""".formatted(id, tipo, Instant.now(), origen,
                correlacionId == null ? "null" : "\"" + correlacionId + "\"", json.writeValueAsString(datos));
        rabbit.send(ConfiguracionRabbit.EXCHANGE, clave, MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(id.toString())
                .build());
        return id;
    }

    /** Siguiente comando de ese tipo para la planilla en la cola simulada. */
    private JsonNode comando(Queue cola, UUID planilla, String tipo) {
        Instant fin = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive(cola.getName(), 500);
            if (mensaje != null) {
                JsonNode sobre = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
                if (tipo.equals(sobre.get("tipo").asString())
                        && planilla.toString().equals(sobre.get("datos").get("planillaId").asString())) {
                    assertThat(mensaje.getMessageProperties().getReceivedRoutingKey()).endsWith("." + tipo);
                    return sobre;
                }
            }
        }
        throw new AssertionError("No llegó " + tipo + " de la planilla " + planilla);
    }

    private JsonNode evento(UUID planilla, String tipo) {
        return comando(eventosObras, planilla, tipo);
    }

    private Map<String, Object> fallido(UUID mensajeId) {
        Instant fin = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive(ConfiguracionRabbit.COLA_FALLIDOS, 500);
            if (mensaje != null && mensajeId.toString().equals(mensaje.getMessageProperties().getMessageId())) {
                return mensaje.getMessageProperties().getHeaders();
            }
        }
        throw new AssertionError("No llegó a plataforma.fallidos el mensaje " + mensajeId);
    }

    private String estadoPlanilla(UUID planilla) {
        return jdbc.queryForObject("select estado from planilla where id = ?", String.class, planilla);
    }

    private String estadoSaga(UUID saga) {
        return jdbc.queryForObject("select estado from saga_aprobacion where id = ?", String.class, saga);
    }

    private String paso(UUID saga) {
        return jdbc.queryForObject("select paso_actual from saga_aprobacion where id = ?", String.class, saga);
    }

    private String cliente() {
        return TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente");
    }

    private String residente() {
        return TokensPrueba.valido(equipo.residente().toString(), "residente");
    }
}
