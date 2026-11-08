package com.constructora.obras.compartido.infraestructura.mensajeria;

/**
 * Consumidor de un tipo de mensaje de la cola obras. Corre dentro de la transacción que registra el id del mensaje
 * en mensaje_procesado. Si los datos están incompletos lanza MensajeInvalidoException (va a fallidos); cualquier
 * otra excepción se reintenta.
 */
public interface ManejadorMensaje {

    /** Tipo del sobre que atiende, por ejemplo "ContratoRegistrado". */
    String tipo();

    void procesar(Sobre sobre);
}
