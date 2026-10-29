package com.constructora.obras.planilla.infraestructura.persistencia;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Tabla planilla (docs/datos.md, migración V3 de la 006) con sus líneas (linea_planilla) y sus fotos adjuntas
 * (planilla_foto, V4 de la 007). El id lo crea el dominio: Persistable evita el SELECT previo al insertar.
 */
@Entity
@Table(name = "planilla")
public class PlanillaEntity implements Persistable<UUID> {

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
    /** Bloqueo optimista: Hibernate la sube en cada UPDATE y falla si otro la cambió antes. */
    @Version
    private int version;

    /** Tabla linea_planilla: clave (planilla_id, partida_id). */
    @ElementCollection
    @CollectionTable(name = "linea_planilla", joinColumns = @JoinColumn(name = "planilla_id"))
    private List<LineaPlanillaEntity> lineas = new ArrayList<>();

    /** Tabla planilla_foto: clave (planilla_id, foto_id). */
    @ElementCollection
    @CollectionTable(name = "planilla_foto", joinColumns = @JoinColumn(name = "planilla_id"))
    @Column(name = "foto_id")
    private Set<UUID> fotoIds = new HashSet<>();

    @Transient
    private boolean nueva = true;

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarGuardada() {
        nueva = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getObraId() {
        return obraId;
    }

    public void setObraId(UUID obraId) {
        this.obraId = obraId;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public LocalDate getPeriodo() {
        return periodo;
    }

    public void setPeriodo(LocalDate periodo) {
        this.periodo = periodo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDescuentoAnticipo() {
        return descuentoAnticipo;
    }

    public void setDescuentoAnticipo(BigDecimal descuentoAnticipo) {
        this.descuentoAnticipo = descuentoAnticipo;
    }

    public BigDecimal getRetencion() {
        return retencion;
    }

    public void setRetencion(BigDecimal retencion) {
        this.retencion = retencion;
    }

    public BigDecimal getNeto() {
        return neto;
    }

    public void setNeto(BigDecimal neto) {
        this.neto = neto;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public UUID getCobroId() {
        return cobroId;
    }

    public void setCobroId(UUID cobroId) {
        this.cobroId = cobroId;
    }

    public String getCobroNumero() {
        return cobroNumero;
    }

    public void setCobroNumero(String cobroNumero) {
        this.cobroNumero = cobroNumero;
    }

    public int getVersion() {
        return version;
    }

    public List<LineaPlanillaEntity> getLineas() {
        return lineas;
    }

    public Set<UUID> getFotoIds() {
        return fotoIds;
    }
}
