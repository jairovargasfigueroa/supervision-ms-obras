package com.constructora.obras.avance.infraestructura.persistencia;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FotoAvanceJpaRepository extends JpaRepository<FotoAvanceEntity, UUID>,
        JpaSpecificationExecutor<FotoAvanceEntity> {
}
