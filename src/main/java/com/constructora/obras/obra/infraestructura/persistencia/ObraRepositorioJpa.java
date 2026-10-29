package com.constructora.obras.obra.infraestructura.persistencia;

import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de ObraRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class ObraRepositorioJpa implements ObraRepositorio {

    private final ObraJpaRepository jpa;
    private final ObraPersistenciaMapper mapper;

    public ObraRepositorioJpa(ObraJpaRepository jpa, ObraPersistenciaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public void guardar(Obra obra) {
        jpa.findById(obra.id()).ifPresentOrElse(
                entidad -> mapper.actualizar(obra, entidad),
                () -> jpa.save(mapper.aEntidad(obra)));
    }

    @Override
    public Optional<Obra> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapper::aDominio);
    }

    @Override
    public List<Obra> listarTodas() {
        return jpa.findAllByOrderByNombreAsc().stream().map(mapper::aDominio).toList();
    }

    @Override
    public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
            boolean comoAlmacenero, boolean comoCliente) {
        return jpa.buscarAsignadas(usuarioId, comoResidente, comoMaestro, comoAlmacenero, comoCliente).stream()
                .map(mapper::aDominio).toList();
    }
}
