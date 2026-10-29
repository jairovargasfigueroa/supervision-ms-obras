package com.constructora.obras.compartido.infraestructura.mensajeria;

import com.constructora.obras.compartido.dominio.ComandoDominio;
import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.compartido.dominio.Reloj;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda un evento en outbox_evento con el sobre común, dentro de la transacción del caso de uso (MANDATORY: si no
 * hay transacción es un error de programación). PublicadorOutbox lo envía después.
 */
@Component
public class Outbox {

    private final OutboxEventoJpaRepository repositorio;
    private final JsonMapper json;
    private final Reloj reloj;

    public Outbox(OutboxEventoJpaRepository repositorio, JsonMapper json, Reloj reloj) {
        this.repositorio = repositorio;
        this.json = json;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID guardar(EventoDominio evento) {
        UUID id = UUID.randomUUID();
        Instant ahora = reloj.ahora().truncatedTo(ChronoUnit.MILLIS);
        Sobre sobre = new Sobre(id, evento.tipo(), Sobre.VERSION, ahora, Sobre.ORIGEN, evento.correlacionId(),
                json.valueToTree(evento));
        repositorio.save(new OutboxEventoEntity(id, evento.tipo(), claveDe(evento), json.writeValueAsString(sobre),
                ahora));
        return id;
    }

    /** evento.obras.&lt;Tipo&gt; para los eventos; comando.&lt;destino&gt;.&lt;Tipo&gt; para los comandos de la saga. */
    static String claveDe(EventoDominio evento) {
        if (evento instanceof ComandoDominio comando) {
            return "comando." + comando.destino() + "." + comando.tipo();
        }
        return "evento." + Sobre.ORIGEN + "." + evento.tipo();
    }
}
