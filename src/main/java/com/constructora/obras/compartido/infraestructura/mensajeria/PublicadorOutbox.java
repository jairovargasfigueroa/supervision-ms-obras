package com.constructora.obras.compartido.infraestructura.mensajeria;

import com.constructora.obras.compartido.dominio.Reloj;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envía cada segundo los mensajes pendientes de outbox_evento al exchange plataforma con su clave
 * (evento.obras.&lt;Tipo&gt; o, para los comandos de la saga, comando.&lt;destino&gt;.&lt;Tipo&gt;) y los marca publicados. Espera la confirmación de RabbitMQ de cada mensaje antes de
 * marcarlo: si RabbitMQ no responde, el mensaje queda pendiente y se reintenta en la próxima vuelta (los
 * consumidores descartan repetidos por id).
 */
@Component
public class PublicadorOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);
    private static final long ESPERA_CONFIRMACION_MS = 5000;

    private final OutboxEventoJpaRepository repositorio;
    private final RabbitTemplate rabbit;
    private final Reloj reloj;

    public PublicadorOutbox(OutboxEventoJpaRepository repositorio, RabbitTemplate rabbit, Reloj reloj) {
        this.repositorio = repositorio;
        this.rabbit = rabbit;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${obras.mensajeria.intervalo-outbox-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEventoEntity> pendientes = repositorio.tomarPendientes();
        for (OutboxEventoEntity evento : pendientes) {
            try {
                rabbit.invoke(operaciones -> {
                    operaciones.send(ConfiguracionRabbit.EXCHANGE, evento.getClave(), mensaje(evento));
                    operaciones.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
                    return null;
                });
            } catch (AmqpException error) {
                // Los ya enviados en esta vuelta quedan marcados; el resto se reintenta en la siguiente
                log.warn("No se pudo publicar {} {}: {}", evento.getTipo(), evento.getId(), error.getMessage());
                return;
            }
            evento.marcarPublicado(reloj.ahora());
        }
    }

    private static Message mensaje(OutboxEventoEntity evento) {
        return MessageBuilder.withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(evento.getId().toString())
                .setType(evento.getTipo())
                .setTimestamp(Date.from(evento.getCreadoEn()))
                .build();
    }
}
