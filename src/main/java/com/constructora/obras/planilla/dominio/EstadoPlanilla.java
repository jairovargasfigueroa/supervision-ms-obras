package com.constructora.obras.planilla.dominio;

/**
 * Estados de la planilla de avance (docs/datos.md, planilla.estado). Solo las APROBADA cuentan para la cantidad
 * ejecutada acumulada y el avance de la obra (docs/reglas-negocio.md, «Avance y plazo»).
 */
public enum EstadoPlanilla {
    BORRADOR,
    PRESENTADA,
    APROBANDO,
    APROBADA,
    OBSERVADA
}
