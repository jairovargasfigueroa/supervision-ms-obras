package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento evento.obras.PlanillaAprobada · {planillaId, obraId, periodo, neto, cobroId, lineas[{partidaId,
 * cantidadPeriodo, monto}]} (docs/contratos.md): la saga terminó bien (paso 5). correlacionId = la saga.
 */
public record PlanillaAprobada(UUID correlacionId, UUID planillaId, UUID obraId, String periodo, BigDecimal neto,
        UUID cobroId, List<Linea> lineas) implements EventoDominio {

    public static final String TIPO = "PlanillaAprobada";

    public PlanillaAprobada {
        Objects.requireNonNull(correlacionId, "correlacionId");
        lineas = List.copyOf(lineas);
    }

    public static PlanillaAprobada de(UUID sagaId, Planilla planilla) {
        return new PlanillaAprobada(sagaId, planilla.id(), planilla.obraId(), planilla.periodo().toString(),
                planilla.totales().neto().valor(), planilla.cobroId(), planilla.lineas().stream()
                        .map(linea -> new Linea(linea.partidaId(), linea.cantidadPeriodo(), linea.monto().valor()))
                        .toList());
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public Object datos() {
        return new Datos(planillaId, obraId, periodo, neto, cobroId, lineas);
    }

    /** Línea de la planilla: cantidad del periodo (3 decimales) y monto (2 decimales). */
    public record Linea(UUID partidaId, BigDecimal cantidadPeriodo, BigDecimal monto) {
    }

    public record Datos(UUID planillaId, UUID obraId, String periodo, BigDecimal neto, UUID cobroId,
            List<Linea> lineas) {
    }
}
