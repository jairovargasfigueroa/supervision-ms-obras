package com.constructora.obras.saga.infraestructura.persistencia;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SagaAprobacionJpaRepository extends JpaRepository<SagaAprobacionEntity, UUID> {

    /** SELECT … FOR UPDATE: la respuesta de un paso y la tarea de vencimiento se esperan una a la otra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SagaAprobacionEntity s where s.id = :id")
    Optional<SagaAprobacionEntity> bloquear(@Param("id") UUID id);

    /** Sagas en curso con el plazo del paso vencido (usa el índice parcial por vence_en). */
    @Query("select s.id from SagaAprobacionEntity s where s.estado = 'EN_CURSO' and s.venceEn < :ahora")
    List<UUID> buscarVencidas(@Param("ahora") Instant ahora);
}
