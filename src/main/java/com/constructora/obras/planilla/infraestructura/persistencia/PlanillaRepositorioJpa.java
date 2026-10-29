package com.constructora.obras.planilla.infraestructura.persistencia;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Adaptador de PlanillaRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class PlanillaRepositorioJpa implements PlanillaRepositorio {

    private static final String BORRADOR = EstadoPlanilla.BORRADOR.name();

    private final PlanillaJpaRepository jpa;
    private final PlanillaPersistenciaMapper mapper;

    public PlanillaRepositorioJpa(PlanillaJpaRepository jpa, PlanillaPersistenciaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Planilla> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapper::aDominio);
    }

    @Override
    public List<Planilla> listarDeObra(UUID obraId, boolean incluirBorradores) {
        List<PlanillaEntity> filas = incluirBorradores
                ? jpa.findByObraIdOrderByNumeroAsc(obraId)
                : jpa.findByObraIdAndEstadoNotOrderByNumeroAsc(obraId, BORRADOR);
        return filas.stream().map(mapper::aDominio).toList();
    }

    @Override
    public boolean hayAbierta(UUID obraId) {
        return jpa.existsByObraIdAndEstadoNot(obraId, EstadoPlanilla.APROBADA.name());
    }

    @Override
    public Optional<Periodo> periodoUltima(UUID obraId) {
        return jpa.buscarUltimoPeriodo(obraId).map(Periodo::de);
    }

    @Override
    public int siguienteNumero(UUID obraId) {
        return jpa.buscarMayorNumero(obraId) + 1;
    }

    @Override
    public Map<UUID, BigDecimal> acumuladosAnteriores(UUID obraId, Periodo periodo) {
        Map<UUID, BigDecimal> acumulados = new HashMap<>();
        jpa.sumarAprobadasAnteriores(obraId, periodo.primerDia())
                .forEach(fila -> acumulados.put(fila.partidaId(), fila.cantidadPeriodo()));
        return acumulados;
    }

    @Override
    public Planilla guardar(Planilla planilla) {
        Optional<PlanillaEntity> existente = jpa.findById(planilla.id());
        if (existente.isEmpty()) {
            try {
                return mapper.aDominio(jpa.saveAndFlush(mapper.aEntidad(planilla)));
            } catch (DataIntegrityViolationException e) {
                // Otra petición creó a la vez la planilla con el mismo número o periodo (únicos por obra)
                throw new ReglaNegocioException(Planilla.PLANILLA_ABIERTA_EXISTE,
                        "Ya hay una planilla sin aprobar en esta obra. Termínala antes de crear otra.");
            }
        }
        PlanillaEntity entidad = existente.get();
        if (entidad.getVersion() != planilla.version()) {
            throw versionDesactualizada();
        }
        mapper.actualizar(planilla, entidad);
        try {
            jpa.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionDesactualizada();
        }
        return mapper.aDominio(entidad);
    }

    private static ReglaNegocioException versionDesactualizada() {
        return new ReglaNegocioException(Planilla.VERSION_DESACTUALIZADA,
                "La planilla cambió, recarga para ver la última versión");
    }
}
