package com.constructora.obras.obra.aplicacion.puerto.salida;

import com.constructora.obras.compartido.dominio.EventoDominio;

/** Publica eventos con el patrón outbox: se guardan en la misma transacción que el dato. */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);
}
