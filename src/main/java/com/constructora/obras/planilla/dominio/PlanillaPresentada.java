package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento evento.obras.PlanillaPresentada · {planillaId, obraId, numero, periodo, neto, reenvio, presentadaEn}
 * (docs/contratos.md, 010): el residente presentó la planilla al cliente; reenvio = true si venía de OBSERVADA. Lo
 * escucha n8n para avisar al cliente por correo. correlacionId = la planilla.
 */
public record PlanillaPresentada(UUID planillaId, UUID obraId, int numero, String periodo, BigDecimal neto,
        boolean reenvio, Instant presentadaEn) implements EventoDominio {

    public static final String TIPO = "PlanillaPresentada";

    public PlanillaPresentada {
        Objects.requireNonNull(planillaId, "planillaId");
        Objects.requireNonNull(presentadaEn, "presentadaEn");
    }

    /**
     * @param planilla planilla ya presentada (PRESENTADA, con su hora de presentación)
     * @param reenvio  true si antes de presentarla estaba OBSERVADA
     */
    public static PlanillaPresentada de(Planilla planilla, boolean reenvio) {
        return new PlanillaPresentada(planilla.id(), planilla.obraId(), planilla.numero(),
                planilla.periodo().toString(), planilla.totales().neto().valor(), reenvio, planilla.presentadaEn());
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public UUID correlacionId() {
        return planillaId;
    }
}
