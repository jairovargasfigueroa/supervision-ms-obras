package com.constructora.obras.planilla.infraestructura.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Detalle de GET /planillas/{id} (docs/contratos.md); lo devuelven también crear, guardar y presentar. */
public record PlanillaResponse(UUID id, UUID obraId, int numero, String periodo, String estado, int version,
        List<Linea> lineas, BigDecimal subtotal, BigDecimal descuentoAnticipo, BigDecimal retencion, BigDecimal neto,
        BigDecimal anticipoPct, BigDecimal retencionPct, String observacion, UUID cobroId, String cobroNumero,
        List<UUID> fotoIds) {

    /** Línea por partida (criterio 3): saldo = cantidad contratada − acumulado total. */
    public record Linea(UUID partidaId, String codigo, String nombre, String unidad, BigDecimal precioUnitario,
            BigDecimal cantidadContratada, BigDecimal acumuladoAnterior, BigDecimal cantidadPeriodo,
            BigDecimal acumuladoTotal, BigDecimal saldo, BigDecimal monto) {
    }
}
