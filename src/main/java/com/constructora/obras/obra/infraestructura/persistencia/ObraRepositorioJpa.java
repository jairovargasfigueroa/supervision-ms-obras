package com.constructora.obras.obra.infraestructura.persistencia;

import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Adaptador de ObraRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class ObraRepositorioJpa implements ObraRepositorio {

    private static final Sort POR_NOMBRE = Sort.by("nombre", "id");
    private static final char ESCAPE = '\\';

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
    public List<Obra> listarTodas(FiltroObras filtro) {
        return jpa.findAll(filtros(filtro), POR_NOMBRE).stream().map(mapper::aDominio).toList();
    }

    @Override
    public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
            boolean comoAlmacenero, boolean comoCliente, FiltroObras filtro) {
        Specification<ObraEntity> asignada = (obra, consulta, cb) -> {
            List<Predicate> papeles = new ArrayList<>();
            if (comoResidente) {
                papeles.add(cb.equal(obra.get("residenteUsuarioId"), usuarioId));
            }
            if (comoMaestro) {
                papeles.add(cb.equal(obra.get("maestroUsuarioId"), usuarioId));
            }
            if (comoAlmacenero) {
                papeles.add(cb.equal(obra.get("almaceneroUsuarioId"), usuarioId));
            }
            if (comoCliente) {
                papeles.add(cb.equal(obra.get("clienteUsuarioId"), usuarioId));
            }
            return papeles.isEmpty() ? cb.disjunction() : cb.or(papeles.toArray(Predicate[]::new));
        };
        return jpa.findAll(asignada.and(filtros(filtro)), POR_NOMBRE).stream().map(mapper::aDominio).toList();
    }

    /** Estado exacto y nombre que contiene la búsqueda, sin distinguir mayúsculas (los comodines van literales). */
    private static Specification<ObraEntity> filtros(FiltroObras filtro) {
        return (obra, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(obra.get("estado"), filtro.estado().name()));
            }
            if (filtro.busqueda() != null) {
                String patron = "%" + escaparComodines(filtro.busqueda().toLowerCase(Locale.ROOT)) + "%";
                condiciones.add(cb.like(cb.lower(obra.get("nombre")), patron, ESCAPE));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
