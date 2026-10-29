package com.constructora.obras.planilla.infraestructura.persistencia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tabla planilla (docs/datos.md, migración V3 de la 006) con sus líneas (linea_planilla). En la 006 solo se lee; la
 * 007 la crea y la guarda.
 */
@Entity
@Table(name = "planilla")
public class PlanillaEntity {

    @Id
    private UUID id;
    @Column(name = "obra_id")
    private UUID obraId;
    private int numero;
    /** Primer día del mes. */
    private LocalDate periodo;
    private String estado;
    private BigDecimal subtotal;
    @Column(name = "descuento_anticipo")
    private BigDecimal descuentoAnticipo;
    private BigDecimal retencion;
    private BigDecimal neto;
    private String observacion;
    @Column(name = "cobro_id")
    private UUID cobroId;
    @Column(name = "cobro_numero")
    private String cobroNumero;
    /** Bloqueo optimista. */
    @Version
    private int version;

    /** Tabla linea_planilla: clave (planilla_id, partida_id). */
    @ElementCollection
    @CollectionTable(name = "linea_planilla", joinColumns = @JoinColumn(name = "planilla_id"))
    private List<LineaPlanillaEntity> lineas = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public UUID getObraId() {
        return obraId;
    }

    public int getNumero() {
        return numero;
    }

    public LocalDate getPeriodo() {
        return periodo;
    }

    public String getEstado() {
        return estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDescuentoAnticipo() {
        return descuentoAnticipo;
    }

    public BigDecimal getRetencion() {
        return retencion;
    }

    public BigDecimal getNeto() {
        return neto;
    }

    public String getObservacion() {
        return observacion;
    }

    public UUID getCobroId() {
        return cobroId;
    }

    public String getCobroNumero() {
        return cobroNumero;
    }

    public int getVersion() {
        return version;
    }

    public List<LineaPlanillaEntity> getLineas() {
        return lineas;
    }
}
