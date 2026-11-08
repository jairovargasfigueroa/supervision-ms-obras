package com.constructora.obras.obra.infraestructura.mensajeria;

import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.compartido.infraestructura.mensajeria.Outbox;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import org.springframework.stereotype.Component;

/** Adaptador del puerto PublicadorEventos: deja el evento en el outbox (lo envía PublicadorOutbox). */
@Component
public class PublicadorEventosOutbox implements PublicadorEventos {

    private final Outbox outbox;

    public PublicadorEventosOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publicar(EventoDominio evento) {
        outbox.guardar(evento);
    }
}
