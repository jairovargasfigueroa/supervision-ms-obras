package com.constructora.obras.saga.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.util.List;

/**
 * Lo que produce una transición de la saga: los mensajes a publicar (en orden), si la saga y la planilla cambiaron (y
 * hay que guardarlas) y, si la respuesta no se esperaba, un texto para el registro.
 */
public record Reaccion(List<EventoDominio> mensajes, boolean sagaCambio, boolean planillaCambio, String nota) {

    public Reaccion {
        mensajes = List.copyOf(mensajes);
    }

    /** Nada que hacer (por ejemplo, una respuesta repetida); la nota explica por qué. */
    public static Reaccion ninguna(String nota) {
        return new Reaccion(List.of(), false, false, nota);
    }
}
