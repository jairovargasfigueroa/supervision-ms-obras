package com.constructora.obras.obra.infraestructura.persistencia;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObraJpaRepository extends JpaRepository<ObraEntity, UUID> {

    List<ObraEntity> findAllByOrderByNombreAsc();

    @Query("""
            select o from ObraEntity o
            where (:residente = true and o.residenteUsuarioId = :usuarioId)
               or (:maestro = true and o.maestroUsuarioId = :usuarioId)
               or (:almacenero = true and o.almaceneroUsuarioId = :usuarioId)
               or (:cliente = true and o.clienteUsuarioId = :usuarioId)
            order by o.nombre""")
    List<ObraEntity> buscarAsignadas(@Param("usuarioId") UUID usuarioId, @Param("residente") boolean residente,
            @Param("maestro") boolean maestro, @Param("almacenero") boolean almacenero,
            @Param("cliente") boolean cliente);
}
