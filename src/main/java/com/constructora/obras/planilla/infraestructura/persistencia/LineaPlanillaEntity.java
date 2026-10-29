package com.constructora.obras.planilla.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

/** Fila de linea_planilla (docs/datos.md): cantidad medida de una partida en la planilla dueña. */
@Embeddable
public class LineaPlanillaEntity {

    @Column(name = "partida_id")
    private UUID partidaId;
    @Column(name = "cantidad_periodo")
    private BigDecimal cantidadPeriodo;
    private BigDecimal monto;

    public UUID getPartidaId() {
        return partidaId;
    }

    public void setPartidaId(UUID partidaId) {
        this.partidaId = partidaId;
    }

    public BigDecimal getCantidadPeriodo() {
        return cantidadPeriodo;
    }

    public void setCantidadPeriodo(BigDecimal cantidadPeriodo) {
        this.cantidadPeriodo = cantidadPeriodo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
}
