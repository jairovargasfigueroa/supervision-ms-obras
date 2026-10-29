package com.constructora.obras.avance.infraestructura.web.dto;

import java.util.List;

/** Respuesta paginada de GET /obras/{obraId}/fotos (convenciones, «Listas y paginación»). */
public record FotoPaginaResponse(List<FotoListadaResponse> items, long total, int pagina, int tamano) {
}
