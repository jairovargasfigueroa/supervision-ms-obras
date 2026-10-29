package com.constructora.obras.avance.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Tabla foto_avance (docs/datos.md). El id lo crea el celular: Persistable evita el SELECT previo al insertar. Las
 * columnas de la capa 3 (clave_archivo_cliente, personas_detectadas, sin_casco) se mapean con la 301 y la 303.
 */
@Entity
@Table(name = "foto_avance")
public class FotoAvanceEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column(name = "obra_id")
    private UUID obraId;
    @Column(name = "partida_id")
    private UUID partidaId;
    @Column(name = "clave_archivo")
    private String claveArchivo;
    @Column(name = "publicada_cliente")
    private boolean publicadaCliente;
    @Column(name = "registrado_por")
    private UUID registradoPor;
    @Column(name = "registrado_en")
    private Instant registradoEn;

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

    public UUID getPartidaId() {
        return partidaId;
    }

    public void setPartidaId(UUID partidaId) {
        this.partidaId = partidaId;
    }

    public String getClaveArchivo() {
        return claveArchivo;
    }

    public void setClaveArchivo(String claveArchivo) {
        this.claveArchivo = claveArchivo;
    }

    public boolean isPublicadaCliente() {
        return publicadaCliente;
    }

    public void setPublicadaCliente(boolean publicadaCliente) {
        this.publicadaCliente = publicadaCliente;
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
