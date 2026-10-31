package com.constructora.obras.obra.infraestructura.persistencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Tabla obra (docs/datos.md). El id lo crea el dominio: Persistable evita el SELECT previo al insertar. */
@Entity
@Table(name = "obra")
public class ObraEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String nombre;
    private String ubicacion;
    private String zona;
    private String estado;
    @Column(name = "cliente_usuario_id")
    private UUID clienteUsuarioId;
    @Column(name = "residente_usuario_id")
    private UUID residenteUsuarioId;
    @Column(name = "maestro_usuario_id")
    private UUID maestroUsuarioId;
    @Column(name = "almacenero_usuario_id")
    private UUID almaceneroUsuarioId;
    @Column(name = "contrato_id")
    private UUID contratoId;
    @Column(name = "monto_contrato")
    private BigDecimal montoContrato;
    @Column(name = "plazo_dias")
    private int plazoDias;
    @Column(name = "anticipo_pct")
    private BigDecimal anticipoPct;
    @Column(name = "retencion_pct")
    private BigDecimal retencionPct;
    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;
    @Column(name = "motivo_rechazo_contrato")
    private String motivoRechazoContrato;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "obra_id", nullable = false, updatable = false)
    @OrderBy("codigo")
    private List<PartidaEntity> partidas = new ArrayList<>();

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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public UUID getClienteUsuarioId() {
        return clienteUsuarioId;
    }

    public void setClienteUsuarioId(UUID clienteUsuarioId) {
        this.clienteUsuarioId = clienteUsuarioId;
    }

    public UUID getResidenteUsuarioId() {
        return residenteUsuarioId;
    }

    public void setResidenteUsuarioId(UUID residenteUsuarioId) {
        this.residenteUsuarioId = residenteUsuarioId;
    }

    public UUID getMaestroUsuarioId() {
        return maestroUsuarioId;
    }

    public void setMaestroUsuarioId(UUID maestroUsuarioId) {
        this.maestroUsuarioId = maestroUsuarioId;
    }

    public UUID getAlmaceneroUsuarioId() {
        return almaceneroUsuarioId;
    }

    public void setAlmaceneroUsuarioId(UUID almaceneroUsuarioId) {
        this.almaceneroUsuarioId = almaceneroUsuarioId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public void setContratoId(UUID contratoId) {
        this.contratoId = contratoId;
    }

    public BigDecimal getMontoContrato() {
        return montoContrato;
    }

    public void setMontoContrato(BigDecimal montoContrato) {
        this.montoContrato = montoContrato;
    }

    public int getPlazoDias() {
        return plazoDias;
    }

    public void setPlazoDias(int plazoDias) {
        this.plazoDias = plazoDias;
    }

    public BigDecimal getAnticipoPct() {
        return anticipoPct;
    }

    public void setAnticipoPct(BigDecimal anticipoPct) {
        this.anticipoPct = anticipoPct;
    }

    public BigDecimal getRetencionPct() {
        return retencionPct;
    }

    public void setRetencionPct(BigDecimal retencionPct) {
        this.retencionPct = retencionPct;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getMotivoRechazoContrato() {
        return motivoRechazoContrato;
    }

    public void setMotivoRechazoContrato(String motivoRechazoContrato) {
        this.motivoRechazoContrato = motivoRechazoContrato;
    }

    public List<PartidaEntity> getPartidas() {
        return partidas;
    }

    public void setPartidas(List<PartidaEntity> partidas) {
        this.partidas = partidas;
    }
}
