package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento evento.obras.PlanillaObservada · {planillaId, obraId, motivo, origen} (docs/contratos.md): la observó el
 * cliente (origen CLIENTE, correlacionId = la planilla) o falló la saga (origen SAGA, correlacionId = la saga).
 */
public record PlanillaObservada(UUID correlacionId, UUID planillaId, UUID obraId, String motivo, String origen)
        implements EventoDominio {

    public static final String TIPO = "PlanillaObservada";
    public static final String CLIENTE = "CLIENTE";
    public static final String SAGA = "SAGA";

    public PlanillaObservada {
        Objects.requireNonNull(correlacionId, "correlacionId");
    }

    public static PlanillaObservada porCliente(Planilla planilla) {
        return new PlanillaObservada(planilla.id(), planilla.id(), planilla.obraId(), planilla.observacion(), CLIENTE);
    }

    public static PlanillaObservada porSaga(UUID sagaId, Planilla planilla) {
        return new PlanillaObservada(sagaId, planilla.id(), planilla.obraId(), planilla.observacion(), SAGA);
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public Object datos() {
        return new Datos(planillaId, obraId, motivo, origen);
    }

    public record Datos(UUID planillaId, UUID obraId, String motivo, String origen) {
    }
}
