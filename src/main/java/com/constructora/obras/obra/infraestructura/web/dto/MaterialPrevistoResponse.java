package com.constructora.obras.obra.infraestructura.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Respuesta de GET /obras/{id}/material-previsto (docs/contratos.md). */
public record MaterialPrevistoResponse(String periodo, boolean hayPlanilla, List<Item> items) {

    public record Item(UUID partidaId, String materialId, BigDecimal cantidadPrevista) {
    }
}
