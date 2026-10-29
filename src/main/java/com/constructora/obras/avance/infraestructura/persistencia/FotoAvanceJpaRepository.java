package com.constructora.obras.avance.infraestructura.persistencia;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FotoAvanceJpaRepository extends JpaRepository<FotoAvanceEntity, UUID>,
        JpaSpecificationExecutor<FotoAvanceEntity> {

    /** 007 · Publica para el cliente las fotos adjuntas a la planilla presentada. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FotoAvanceEntity f set f.publicadaCliente = true where f.id in :ids")
    void publicar(@Param("ids") Collection<UUID> ids);
}
