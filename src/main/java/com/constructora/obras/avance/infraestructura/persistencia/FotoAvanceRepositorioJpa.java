package com.constructora.obras.avance.infraestructura.persistencia;

import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Adaptador de FotoAvanceRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class FotoAvanceRepositorioJpa implements FotoAvanceRepositorio {

    /** Campo de la API (orden) → atributo de FotoAvanceEntity. */
    private static final Map<String, String> ATRIBUTO_DE_ORDEN = Map.of("registradoEn", "registradoEn");

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

    @Override
    public Pagina<FotoAvance> listar(UUID obraId, UUID partidaId, boolean soloPublicadas, Paginacion paginacion) {
        Specification<FotoAvanceEntity> filtro = (foto, consulta, criterio) -> criterio.equal(foto.get("obraId"),
                obraId);
        if (partidaId != null) {
            filtro = filtro.and((foto, consulta, criterio) -> criterio.equal(foto.get("partidaId"), partidaId));
        }
        if (soloPublicadas) {
            filtro = filtro.and((foto, consulta, criterio) -> criterio.isTrue(foto.get("publicadaCliente")));
        }
        Sort.Direction direccion = paginacion.ascendente() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort orden = Sort.by(direccion, ATRIBUTO_DE_ORDEN.get(paginacion.orden())).and(Sort.by(direccion, "id"));
        Page<FotoAvanceEntity> pagina = jpa.findAll(filtro,
                PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(), orden));
        return new Pagina<>(pagina.getContent().stream().map(mapper::aDominio).toList(), pagina.getTotalElements(),
                paginacion.pagina(), paginacion.tamano());
    }
}
