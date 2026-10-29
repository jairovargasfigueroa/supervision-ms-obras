package com.constructora.obras.avance.infraestructura.persistencia;

import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de FotoAvanceRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class FotoAvanceRepositorioJpa implements FotoAvanceRepositorio {

    private final FotoAvanceJpaRepository jpa;
    private final FotoAvancePersistenciaMapper mapper;

    public FotoAvanceRepositorioJpa(FotoAvanceJpaRepository jpa, FotoAvancePersistenciaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public void guardar(FotoAvance foto) {
        jpa.save(mapper.aEntidad(foto));
    }

    @Override
    public Optional<FotoAvance> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapper::aDominio);
    }
}
