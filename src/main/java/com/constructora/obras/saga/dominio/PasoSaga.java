package com.constructora.obras.saga.dominio;

/**
 * Pasos de la saga que esperan la respuesta de otro servicio (008, diseño): el número es el de la tabla de pasos y el
 * que aparece en «El paso X no respondió a tiempo». El paso 1 (planilla APROBANDO) y el 5 (APROBADA) los hace Obras
 * sin esperar a nadie.
 */
public enum PasoSaga {
    /** Finanzas reserva el neto contra el saldo del contrato. */
    RESERVAR_SALDO(2),
    /** Materiales cierra los movimientos del periodo de la obra. */
    CERRAR_CONSUMOS(3),
    /** Finanzas emite el cobro y consume la reserva. */
    EMITIR_COBRO(4);

    private final int numero;

    PasoSaga(int numero) {
        this.numero = numero;
    }

    public int numero() {
        return numero;
    }
}
