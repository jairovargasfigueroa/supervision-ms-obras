package com.constructora.obras.compartido.infraestructura.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.Comando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.ContratoComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.PartidaComando;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.soporte.Esperar;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tarea 4: ObraRegistrada sale del outbox al exchange plataforma con el sobre común y queda marcada publicada. */
class PublicadorOutboxIT extends PruebaIntegracion {

    @Autowired
    private RegistrarObraCasoUso registrar;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin admin;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private Queue escucha;

    @BeforeEach
    void escucharEventosDeObras() {
        escucha = new Queue("prueba." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(escucha);
        admin.declareBinding(BindingBuilder.bind(escucha).to(new TopicExchange(ConfiguracionRabbit.EXCHANGE))
                .with("evento.obras.#"));
    }

    @AfterEach
    void borrarCola() {
        admin.deleteQueue(escucha.getName());
    }

    @Test
    void publicaObraRegistradaConElSobreComun() {
        Asignaciones asignaciones = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        Instant antes = Instant.now();
        UUID obraId = registrar.ejecutar(new Comando("Edificio Las Palmas", "Av. Banzer", LocalDate.of(2026, 11, 1),
                asignaciones, new ContratoComando(new BigDecimal("1250000"), 300, BigDecimal.TEN,
                        BigDecimal.valueOf(7), LocalDate.of(2026, 10, 20)),
                List.of(new PartidaComando("P-01", "Losa", "m2", BigDecimal.valueOf(2500), new BigDecimal("500"),
                        null, List.of())))).obra().id();

        Message mensaje = recibirDeLaObra(obraId);
        JsonNode sobre = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));

        assertThat(mensaje.getMessageProperties().getReceivedRoutingKey()).isEqualTo("evento.obras.ObraRegistrada");
        assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(mensaje.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(sobre.get("id").asString());

        assertThat(nombres(sobre)).containsExactlyInAnyOrder("id", "tipo", "version", "ocurridoEn", "origen",
                "correlacionId", "datos");
        assertThat(sobre.get("tipo").asString()).isEqualTo("ObraRegistrada");
        assertThat(sobre.get("version").asInt()).isEqualTo(1);
        assertThat(sobre.get("origen").asString()).isEqualTo("obras");
        assertThat(sobre.get("correlacionId").asString()).isEqualTo(obraId.toString());
        assertThat(Instant.parse(sobre.get("ocurridoEn").asString())).isAfterOrEqualTo(antes.minusSeconds(1));
        assertThat(sobre.get("ocurridoEn").asString()).endsWith("Z");

        JsonNode datos = sobre.get("datos");
        assertThat(nombres(datos)).containsExactlyInAnyOrder("obraId", "nombre", "fechaInicio", "asignaciones",
                "contrato");
        assertThat(datos.get("obraId").asString()).isEqualTo(obraId.toString());
        assertThat(datos.get("nombre").asString()).isEqualTo("Edificio Las Palmas");
        assertThat(datos.get("fechaInicio").asString()).isEqualTo("2026-11-01");
        assertThat(nombres(datos.get("asignaciones"))).containsExactlyInAnyOrder("residenteId", "maestroId",
                "almaceneroId", "clienteId");
        assertThat(datos.get("asignaciones").get("clienteId").asString())
                .isEqualTo(asignaciones.clienteId().toString());
        JsonNode contrato = datos.get("contrato");
        assertThat(nombres(contrato)).containsExactlyInAnyOrder("monto", "plazoDias", "anticipoPct", "retencionPct",
                "firmadoEn");
        assertThat(contrato.get("monto").isNumber()).isTrue();
        // Con 2 decimales en el texto del mensaje (al leerlo como número, Jackson lo vuelve double)
        assertThat(new String(mensaje.getBody(), StandardCharsets.UTF_8)).containsPattern("\"monto\":\\s*1250000\\.00\\b");
        assertThat(contrato.get("plazoDias").asInt()).isEqualTo(300);
        assertThat(contrato.get("anticipoPct").decimalValue()).isEqualByComparingTo("10");
        assertThat(contrato.get("retencionPct").decimalValue()).isEqualByComparingTo("7");
        assertThat(contrato.get("firmadoEn").asString()).isEqualTo("2026-10-20");

        Esperar.hasta(Duration.ofSeconds(5), () -> jdbc.queryForObject(
                "select publicado_en is not null from outbox_evento where id = ?::uuid", Boolean.class,
                sobre.get("id").asString()));
    }

    private Message recibirDeLaObra(UUID obraId) {
        Instant fin = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive(escucha.getName(), 1000);
            if (mensaje != null && new String(mensaje.getBody(), StandardCharsets.UTF_8).contains(obraId.toString())) {
                return mensaje;
            }
        }
        throw new AssertionError("No llegó ObraRegistrada de la obra " + obraId);
    }

    private static List<String> nombres(JsonNode nodo) {
        return nodo.propertyNames().stream().toList();
    }
}
