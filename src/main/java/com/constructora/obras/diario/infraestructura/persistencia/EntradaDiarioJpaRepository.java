package com.constructora.obras.diario.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaDiarioJpaRepository extends JpaRepository<EntradaDiarioEntity, UUID> {

    /**
     * Inserta la entrada salvo que choque con la clave primaria (mismo id) o con el único (obra_id, fecha). Si otra
     * transacción está guardando la misma clave, espera a que termine.
     *
     * @return 1 si la guardó, 0 si ya existía
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
            values (:id, :obraId, :fecha, :clima, :texto, :registradoPor, :registradoEn)
            on conflict do nothing""", nativeQuery = true)
    int insertarSiNoExiste(@Param("id") UUID id, @Param("obraId") UUID obraId, @Param("fecha") LocalDate fecha,
            @Param("clima") String clima, @Param("texto") String texto,
            @Param("registradoPor") UUID registradoPor, @Param("registradoEn") Instant registradoEn);

    Optional<EntradaDiarioEntity> findByObraIdAndFecha(UUID obraId, LocalDate fecha);

    List<EntradaDiarioEntity> findByObraIdAndFechaBetweenOrderByFechaDesc(UUID obraId, LocalDate desde,
            LocalDate hasta);
}
