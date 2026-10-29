package com.constructora.obras.compartido.infraestructura.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabla outbox_evento (docs/datos.md): payload es el sobre completo; id es el id del mensaje. */
@Entity
@Table(name = "outbox_evento")
public class OutboxEventoEntity {

    @Id
    private UUID id;
    private String tipo;
    /** Clave de enrutamiento: evento.obras.&lt;Tipo&gt;, o comando.&lt;destino&gt;.&lt;Tipo&gt; en la saga (008). */
    private String clave;
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;
    @Column(name = "creado_en")
    private Instant creadoEn;
    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected OutboxEventoEntity() {
    }

    public OutboxEventoEntity(UUID id, String tipo, String clave, String payload, Instant creadoEn) {
        this.id = id;
        this.tipo = tipo;
        this.clave = clave;
        this.payload = payload;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public String getClave() {
        return clave;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public void marcarPublicado(Instant cuando) {
        this.publicadoEn = cuando;
    }
}
