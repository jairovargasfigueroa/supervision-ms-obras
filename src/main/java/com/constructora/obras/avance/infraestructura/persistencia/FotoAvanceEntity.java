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
 * Tabla foto_avance (docs/datos.md). El id lo crea el celular: Persistable evita el SELECT previo al insertar. La
 * detección de casco y chaleco (301) son cuatro columnas que se llenan juntas o quedan en null; clave_archivo_cliente
 * se mapea con la 303.
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
    @Column(name = "personas_detectadas")
    private Integer personasDetectadas;
    @Column(name = "sin_casco")
    private Integer sinCasco;
    @Column(name = "sin_chaleco")
    private Integer sinChaleco;
    @Column(name = "version_modelo_epp")
    private String versionModeloEpp;

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

    public Integer getPersonasDetectadas() {
        return personasDetectadas;
    }

    public void setPersonasDetectadas(Integer personasDetectadas) {
        this.personasDetectadas = personasDetectadas;
    }

    public Integer getSinCasco() {
        return sinCasco;
    }

    public void setSinCasco(Integer sinCasco) {
        this.sinCasco = sinCasco;
    }

    public Integer getSinChaleco() {
        return sinChaleco;
    }

    public void setSinChaleco(Integer sinChaleco) {
        this.sinChaleco = sinChaleco;
    }

    public String getVersionModeloEpp() {
        return versionModeloEpp;
    }

    public void setVersionModeloEpp(String versionModeloEpp) {
        this.versionModeloEpp = versionModeloEpp;
    }
}
