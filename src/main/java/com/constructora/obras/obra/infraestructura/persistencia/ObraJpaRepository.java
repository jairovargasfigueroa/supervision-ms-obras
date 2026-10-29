package com.constructora.obras.obra.infraestructura.persistencia;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Las listas (GET /obras) se arman con Specification: visibilidad y filtros (ObraRepositorioJpa). */
public interface ObraJpaRepository extends JpaRepository<ObraEntity, UUID>, JpaSpecificationExecutor<ObraEntity> {
}
