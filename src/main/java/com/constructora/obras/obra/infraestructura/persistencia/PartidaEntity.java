package com.constructora.obras.obra.infraestructura.persistencia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tabla partida (docs/datos.md). La columna obra_id la maneja ObraEntity.partidas. */
@Entity
@Table(name = "partida")
public class PartidaEntity {

    @Id
    private UUID id;
    private String codigo;
    private String nombre;
    private String unidad;
    @Column(name = "cantidad_contratada")
    private BigDecimal cantidadContratada;
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;
    private String meses;

    /** Tabla insumo_previsto: clave (partida_id, material_id). */
    @ElementCollection
    @CollectionTable(name = "insumo_previsto", joinColumns = @JoinColumn(name = "partida_id"))
    @OrderBy("materialId")
    private List<InsumoPrevistoEntity> insumos = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public BigDecimal getCantidadContratada() {
        return cantidadContratada;
    }

    public void setCantidadContratada(BigDecimal cantidadContratada) {
        this.cantidadContratada = cantidadContratada;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public String getMeses() {
        return meses;
    }

    public void setMeses(String meses) {
        this.meses = meses;
    }

    public List<InsumoPrevistoEntity> getInsumos() {
        return insumos;
    }

    public void setInsumos(List<InsumoPrevistoEntity> insumos) {
        this.insumos = insumos;
    }
}
