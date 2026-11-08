package com.constructora.obras.compartido.infraestructura.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Tabla mensaje_procesado (docs/datos.md): inbox, el id de cada mensaje ya procesado. */
@Entity
@Table(name = "mensaje_procesado")
public class MensajeProcesadoEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id_mensaje")
    private UUID idMensaje;
    @Column(name = "procesado_en")
    private Instant procesadoEn;

    protected MensajeProcesadoEntity() {
    }

    public MensajeProcesadoEntity(UUID idMensaje, Instant procesadoEn) {
        this.idMensaje = idMensaje;
        this.procesadoEn = procesadoEn;
    }

    @Override
    public UUID getId() {
        return idMensaje;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    /** Siempre se inserta: si el id ya existe, la clave primaria lo rechaza (otro consumidor lo tomó). */
    @Override
    public boolean isNew() {
        return true;
    }
}
