package com.constructora.obras.obra.infraestructura.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de POST /obras (docs/contratos.md). Aquí se valida el formato (400 DATOS_INVALIDOS); las reglas del
 * contrato, códigos, insumos y presupuesto las valida el dominio (422). Montos con hasta 2 decimales y cantidades
 * con hasta 3, como en la base (convenciones, «Nombres»). La zona (401) es opcional: un valor fuera de la lista también
 * es 400 con errores[].campo = zona.
 */
public record RegistrarObraRequest(
        @NotBlank(message = "Es obligatorio") @Size(max = 150, message = "Máximo 150 caracteres") String nombre,
        @NotBlank(message = "Es obligatorio") @Size(max = 200, message = "Máximo 200 caracteres") String ubicacion,
        @Pattern(regexp = "NORTE|SUR|ESTE|OESTE|CENTRO", message = "Debe ser NORTE, SUR, ESTE, OESTE o CENTRO")
        String zona,
        @NotNull(message = "Es obligatoria") LocalDate fechaInicio,
        @NotNull(message = "Es obligatorio") @Valid AsignacionesRequest asignaciones,
        @NotNull(message = "Es obligatorio") @Valid ContratoRequest contrato,
        @NotEmpty(message = "Debe tener al menos una partida")
        List<@NotNull(message = "Es obligatoria") @Valid PartidaRequest> partidas) {

    public record AsignacionesRequest(
            @NotNull(message = "Es obligatorio") UUID residenteId,
            @NotNull(message = "Es obligatorio") UUID maestroId,
            @NotNull(message = "Es obligatorio") UUID almaceneroId,
            @NotNull(message = "Es obligatorio") UUID clienteId) {
    }

    /** Monto > 0, plazo > 0 y porcentajes: los valida el dominio (CONTRATO_INVALIDO). */
    public record ContratoRequest(
            @NotNull(message = "Es obligatorio")
            @Digits(integer = 12, fraction = 2, message = "Hasta 12 enteros y 2 decimales") BigDecimal monto,
            @NotNull(message = "Es obligatorio") Integer plazoDias,
            @NotNull(message = "Es obligatorio")
            @Digits(integer = 3, fraction = 2, message = "Hasta 2 decimales") BigDecimal anticipoPct,
            @NotNull(message = "Es obligatorio")
            @Digits(integer = 3, fraction = 2, message = "Hasta 2 decimales") BigDecimal retencionPct,
            @NotNull(message = "Es obligatoria") LocalDate firmadoEn) {
    }

    public record PartidaRequest(
            @NotBlank(message = "Es obligatorio") String codigo,
            @NotBlank(message = "Es obligatorio") String nombre,
            @NotBlank(message = "Es obligatoria") String unidad,
            @NotNull(message = "Es obligatoria") @Positive(message = "Debe ser mayor a 0")
            @Digits(integer = 11, fraction = 3, message = "Hasta 3 decimales") BigDecimal cantidadContratada,
            @NotNull(message = "Es obligatorio") @Positive(message = "Debe ser mayor a 0")
            @Digits(integer = 12, fraction = 2, message = "Hasta 2 decimales") BigDecimal precioUnitario,
            String meses,
            List<@NotNull(message = "Es obligatorio") @Valid InsumoRequest> insumos) {
    }

    public record InsumoRequest(
            @NotBlank(message = "Es obligatorio") String materialId,
            @NotNull(message = "Es obligatoria") @Positive(message = "Debe ser mayor a 0")
            @Digits(integer = 11, fraction = 3, message = "Hasta 3 decimales") BigDecimal cantidadPorUnidad) {
    }
}
