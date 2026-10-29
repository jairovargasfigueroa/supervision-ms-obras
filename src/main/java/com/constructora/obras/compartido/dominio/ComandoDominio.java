package com.constructora.obras.compartido.dominio;

/**
 * Orden que Obras le da a otro servicio en la saga «aprobar planilla» (convenciones, «Eventos y comandos»): viaja
 * con la clave comando.&lt;destino&gt;.&lt;Tipo&gt; en lugar de evento.obras.&lt;Tipo&gt;. El tipo va en infinitivo
 * (ReservarSaldo).
 */
public interface ComandoDominio extends EventoDominio {

    /** Servicio que lo ejecuta: "finanzas" o "materiales". */
    String destino();
}
