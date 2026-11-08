package com.constructora.obras.obra.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Fila de insumo_previsto (docs/datos.md): material previsto por unidad de la partida dueña. */
@Embeddable
public class InsumoPrevistoEntity {

    @Column(name = "material_id")
    private String materialId;
    @Column(name = "cantidad_por_unidad")
    private BigDecimal cantidadPorUnidad;

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getCantidadPorUnidad() {
        return cantidadPorUnidad;
    }

    public void setCantidadPorUnidad(BigDecimal cantidadPorUnidad) {
        this.cantidadPorUnidad = cantidadPorUnidad;
    }
}
