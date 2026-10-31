package com.constructora.obras.obra.infraestructura.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalle de la obra: respuesta de GET /obras/{id} y de POST /obras (docs/contratos.md). zona es null sin zona (401). */
public record ObraResponse(
        UUID id,
        String nombre,
        String ubicacion,
        String zona,
        String estado,
        String motivoRechazoContrato,
        LocalDate fechaInicio,
        Asignaciones asignaciones,
        Contrato contrato,
        BigDecimal avancePct,
        BigDecimal plazoPct,
        boolean enRiesgo,
        List<Partida> partidas) {

    public record Asignaciones(UUID residenteId, UUID maestroId, UUID almaceneroId, UUID clienteId) {
    }

    public record Contrato(UUID contratoId, BigDecimal monto, int plazoDias, BigDecimal anticipoPct,
            BigDecimal retencionPct) {
    }

    public record Partida(UUID id, String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
            BigDecimal precioUnitario, String meses, BigDecimal ejecutadaAcumulada, BigDecimal avancePct,
            List<Insumo> insumos) {
    }

    public record Insumo(String materialId, BigDecimal cantidadPorUnidad) {
    }
}
