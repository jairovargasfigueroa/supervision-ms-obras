package com.constructora.obras.diario.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tabla entrada_diario (docs/datos.md, 103). Se lee con JPA; se inserta con INSERT … ON CONFLICT DO NOTHING
 * (EntradaDiarioJpaRepository.insertarSiNoExiste) para que la restricción única no aborte la transacción.
 */
@Entity
@Table(name = "entrada_diario")
public class EntradaDiarioEntity {

    @Id
    private UUID id;
    @Column(name = "obra_id")
    private UUID obraId;
    private LocalDate fecha;
    private String clima;
    private String texto;
    @Column(name = "registrado_por")
    private UUID registradoPor;
    @Column(name = "registrado_en")
    private Instant registradoEn;

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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getClima() {
        return clima;
    }

    public void setClima(String clima) {
        this.clima = clima;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(UUID registradoPor) {
        this.registradoPor = registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(Instant registradoEn) {
        this.registradoEn = registradoEn;
    }
}
