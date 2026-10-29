package com.constructora.obras.compartido.infraestructura.mensajeria;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeProcesadoJpaRepository extends JpaRepository<MensajeProcesadoEntity, UUID> {
}
