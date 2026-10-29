package com.constructora.obras.compartido.infraestructura.mensajeria;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventoJpaRepository extends JpaRepository<OutboxEventoEntity, UUID> {

    /**
     * Pendientes en orden de creación. FOR UPDATE SKIP LOCKED: si hay dos instancias del servicio, cada mensaje lo
     * toma una sola.
     */
    @Query(value = """
            select * from outbox_evento
            where publicado_en is null
            order by creado_en
            limit 100
            for update skip locked""", nativeQuery = true)
    List<OutboxEventoEntity> tomarPendientes();
}
