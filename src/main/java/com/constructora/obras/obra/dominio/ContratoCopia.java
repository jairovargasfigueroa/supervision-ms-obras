package com.constructora.obras.obra.dominio;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Copia del contrato que Obras necesita para calcular la planilla: monto, plazo, anticipo y retención. El contrato
 * es de Finanzas; contratoId es nulo hasta que llega ContratoRegistrado.
 */
public record ContratoCopia(UUID contratoId, Dinero monto, int plazoDias, BigDecimal anticipoPct,
        BigDecimal retencionPct) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public ContratoCopia {
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(anticipoPct, "anticipoPct");
        Objects.requireNonNull(retencionPct, "retencionPct");
        anticipoPct = anticipoPct.setScale(2, RoundingMode.HALF_UP);
        retencionPct = retencionPct.setScale(2, RoundingMode.HALF_UP);
    }

    /** Contrato todavía sin registrar en Finanzas. */
    public static ContratoCopia nuevo(Dinero monto, int plazoDias, BigDecimal anticipoPct, BigDecimal retencionPct) {
        return new ContratoCopia(null, monto, plazoDias, anticipoPct, retencionPct);
    }

    public ContratoCopia conContratoId(UUID id) {
        return new ContratoCopia(id, monto, plazoDias, anticipoPct, retencionPct);
    }

    /**
     * Monto > 0, plazo > 0, anticipo y retención entre 0 y 100 y anticipo + retención < 100
     * (docs/reglas-negocio.md, «Contrato»). Si falla alguna: CONTRATO_INVALIDO con cada regla en errores.
     */
    void validar() {
        List<ErrorCampo> errores = new ArrayList<>();
        if (!monto.esPositivo()) {
            errores.add(new ErrorCampo("contrato.monto", "El monto del contrato debe ser mayor a 0"));
        }
        if (plazoDias <= 0) {
            errores.add(new ErrorCampo("contrato.plazoDias", "El plazo del contrato debe ser mayor a 0 días"));
        }
        boolean anticipoEnRango = entre0y100(anticipoPct);
        boolean retencionEnRango = entre0y100(retencionPct);
        if (!anticipoEnRango) {
            errores.add(new ErrorCampo("contrato.anticipoPct", "El anticipo debe estar entre 0 % y 100 %"));
        }
        if (!retencionEnRango) {
            errores.add(new ErrorCampo("contrato.retencionPct", "La retención debe estar entre 0 % y 100 %"));
        }
        if (anticipoEnRango && retencionEnRango && anticipoPct.add(retencionPct).compareTo(CIEN) >= 0) {
            errores.add(new ErrorCampo("contrato.retencionPct",
                    "El anticipo más la retención deben sumar menos de 100 %"));
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException("CONTRATO_INVALIDO", "Los datos del contrato no son válidos.", errores);
        }
    }

    private static boolean entre0y100(BigDecimal valor) {
        return valor.signum() >= 0 && valor.compareTo(CIEN) <= 0;
    }
}
