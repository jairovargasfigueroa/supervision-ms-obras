package com.constructora.obras.saga.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.util.List;

/**
 * Lo que produce una transición de la saga: los mensajes a publicar (en orden), si la saga y la planilla cambiaron (y
 * hay que guardarlas) y, si la respuesta no se esperaba, un texto para el registro. incidente marca lo que no debería
 * pasar y alguien tiene que revisar (un CobroEmitido con la saga ya compensada).
 */
public record Reaccion(List<EventoDominio> mensajes, boolean sagaCambio, boolean planillaCambio, String nota,
        boolean incidente) {

    public Reaccion {
        mensajes = List.copyOf(mensajes);
    }

    /** Transición normal: mensajes y qué cambió, sin nota. */
    public static Reaccion de(List<EventoDominio> mensajes, boolean sagaCambio, boolean planillaCambio) {
        return new Reaccion(mensajes, sagaCambio, planillaCambio, null, false);
    }

    /** Nada que hacer (por ejemplo, una respuesta repetida); la nota explica por qué. */
    public static Reaccion ninguna(String nota) {
        return new Reaccion(List.of(), false, false, nota, false);
    }

    /** Respuesta tardía que se compensa sin cambiar la saga ni la planilla. */
    public static Reaccion compensacionTardia(EventoDominio compensacion, String nota) {
        return new Reaccion(List.of(compensacion), false, false, nota, false);
    }

    /** Algo que no debería pasar: no se cambia nada y se registra como incidente. */
    public static Reaccion incidente(String nota) {
        return new Reaccion(List.of(), false, false, nota, true);
    }
}
