package com.constructora.obras.planilla.infraestructura.persistencia;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.PlanillaCorta;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de PlanillasObraRepositorio (lectura para la 006) sobre JPA. */
@Repository
public class PlanillasObraRepositorioJpa implements PlanillasObraRepositorio {

    private final PlanillaJpaRepository jpa;

    public PlanillasObraRepositorioJpa(PlanillaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, Map<UUID, BigDecimal>> ejecutadaAcumulada(Collection<UUID> obraIds) {
        if (obraIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<UUID, BigDecimal>> porObra = new HashMap<>();
        for (PlanillaJpaRepository.EjecutadaFila fila : jpa.sumarEjecutadaAprobada(obraIds)) {
            porObra.computeIfAbsent(fila.obraId(), id -> new HashMap<>()).put(fila.partidaId(), fila.cantidad());
        }
        return porObra;
    }

    @Override
    public Map<UUID, PlanillaCorta> ultimas(Collection<UUID> obraIds) {
        if (obraIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PlanillaCorta> ultimas = new HashMap<>();
        for (PlanillaJpaRepository.UltimaFila fila : jpa.buscarUltimas(obraIds)) {
            ultimas.put(fila.obraId(),
                    new PlanillaCorta(Periodo.de(fila.periodo()), EstadoPlanilla.valueOf(fila.estado())));
        }
        return ultimas;
    }

    @Override
    public Optional<Map<UUID, BigDecimal>> cantidadesDelPeriodo(UUID obraId, Periodo periodo) {
        if (!jpa.existsByObraIdAndPeriodo(obraId, periodo.primerDia())) {
            return Optional.empty();
        }
        Map<UUID, BigDecimal> cantidades = new HashMap<>();
        jpa.buscarLineasDelPeriodo(obraId, periodo.primerDia())
                .forEach(linea -> cantidades.put(linea.partidaId(), linea.cantidadPeriodo()));
        return Optional.of(cantidades);
    }
}
