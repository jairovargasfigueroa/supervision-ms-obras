package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.Periodo;
import java.util.Objects;

/** Periodo y estado de una planilla: lo que muestra la lista de obras como «última planilla» (006). */
public record PlanillaCorta(Periodo periodo, EstadoPlanilla estado) {

    public PlanillaCorta {
        Objects.requireNonNull(periodo, "periodo");
        Objects.requireNonNull(estado, "estado");
    }
}
