package com.constructora.obras.compartido.infraestructura.mensajeria;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Exchange, colas y enlaces de Obras (convenciones, «Eventos y comandos»). En desarrollo ya los declara infra
 * (infra/rabbitmq/definiciones.json); se declaran aquí con los mismos argumentos (durables, sin argumentos) para que
 * el servicio también funcione contra un RabbitMQ vacío, como el de las pruebas. Declarar algo que ya existe igual
 * no cambia nada.
 */
@Configuration
@EnableScheduling
public class ConfiguracionRabbit {

    public static final String EXCHANGE = "plataforma";
    public static final String COLA = "obras";
    public static final String COLA_FALLIDOS = "plataforma.fallidos";
    /** Claves que escucha la cola de Obras: respuestas al contrato (002) y a los comandos de la saga (008). */
    static final String[] CLAVES_ESCUCHADAS = {
            "evento.finanzas.ContratoRegistrado",
            "evento.finanzas.ContratoRechazado",
            "evento.finanzas.SaldoReservado",
            "evento.finanzas.SaldoInsuficiente",
            "evento.materiales.ConsumosCerrados",
            "evento.materiales.CierreFallido",
            "evento.finanzas.CobroEmitido",
            "evento.finanzas.CobroFallido",
    };

    @Bean
    Declarables declaracionesRabbit() {
        TopicExchange plataforma = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        Queue obras = QueueBuilder.durable(COLA).build();
        Queue fallidos = QueueBuilder.durable(COLA_FALLIDOS).build();
        List<Declarable> declarables = new ArrayList<>(List.of(plataforma, obras, fallidos));
        for (String clave : CLAVES_ESCUCHADAS) {
            Binding enlace = BindingBuilder.bind(obras).to(plataforma).with(clave);
            declarables.add(enlace);
        }
        return new Declarables(declarables);
    }
}
