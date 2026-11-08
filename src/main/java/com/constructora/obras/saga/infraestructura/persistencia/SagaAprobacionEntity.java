package com.constructora.obras.saga.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tabla saga_aprobacion (docs/datos.md, V5 de la 008). detalle es el JSON con reservaId, cobroId, cobroNumero y
 * motivo (los que todavía no hay no figuran).
 */
@Entity
@Table(name = "saga_aprobacion")
public class SagaAprobacionEntity {

    @Id
    private UUID id;
    @Column(name = "planilla_id")
    private UUID planillaId;
    @Column(name = "paso_actual")
    private String pasoActual;
    private String estado;
    @JdbcTypeCode(SqlTypes.JSON)
    private String detalle;
    @Column(name = "vence_en")
    private Instant venceEn;
    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    protected SagaAprobacionEntity() {
    }

    public SagaAprobacionEntity(UUID id, UUID planillaId) {
        this.id = id;
        this.planillaId = planillaId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanillaId() {
        return planillaId;
    }

    public String getPasoActual() {
        return pasoActual;
    }

    public void setPasoActual(String pasoActual) {
        this.pasoActual = pasoActual;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public Instant getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Instant venceEn) {
        this.venceEn = venceEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }

    public void setActualizadaEn(Instant actualizadaEn) {
        this.actualizadaEn = actualizadaEn;
    }
}
