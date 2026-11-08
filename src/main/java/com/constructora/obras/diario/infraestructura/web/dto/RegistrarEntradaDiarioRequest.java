package com.constructora.obras.diario.infraestructura.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de POST /obras/{obraId}/diario (docs/contratos.md). El formato de clima (uno de los cuatro valores) y del
 * texto (10 a 2000 caracteres sin los espacios de los extremos) lo valida el dominio, con el campo en errores.
 */
public record RegistrarEntradaDiarioRequest(
        @NotNull UUID id,
        @NotNull LocalDate fecha,
        @NotNull String clima,
        @NotNull String texto) {
}
