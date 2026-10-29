package com.constructora.obras.planilla.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Planillas en la 006: solo lectura (la 007 agrega crear y guardar). */
public interface PlanillaJpaRepository extends Repository<PlanillaEntity, UUID> {

    /** Suma de cantidad_periodo por obra y partida, solo de planillas APROBADA. */
    @Query("""
            select new com.constructora.obras.planilla.infraestructura.persistencia.PlanillaJpaRepository$EjecutadaFila(
                p.obraId, l.partidaId, sum(l.cantidadPeriodo))
            from PlanillaEntity p join p.lineas l
            where p.obraId in :obraIds and p.estado = 'APROBADA'
            group by p.obraId, l.partidaId""")
    List<EjecutadaFila> sumarEjecutadaAprobada(@Param("obraIds") Collection<UUID> obraIds);

    /** Planilla de periodo más reciente de cada obra (el periodo es único por obra). */
    @Query("""
            select new com.constructora.obras.planilla.infraestructura.persistencia.PlanillaJpaRepository$UltimaFila(
                p.obraId, p.periodo, p.estado)
            from PlanillaEntity p
            where p.obraId in :obraIds
              and p.periodo = (select max(q.periodo) from PlanillaEntity q where q.obraId = p.obraId)""")
    List<UltimaFila> buscarUltimas(@Param("obraIds") Collection<UUID> obraIds);

    /** Líneas de la planilla del periodo de la obra (cualquier estado); vacía si no hay planilla. */
    @Query("""
            select new com.constructora.obras.planilla.infraestructura.persistencia.PlanillaJpaRepository$LineaFila(
                l.partidaId, l.cantidadPeriodo)
            from PlanillaEntity p join p.lineas l
            where p.obraId = :obraId and p.periodo = :periodo""")
    List<LineaFila> buscarLineasDelPeriodo(@Param("obraId") UUID obraId, @Param("periodo") LocalDate periodo);

    boolean existsByObraIdAndPeriodo(UUID obraId, LocalDate periodo);

    record EjecutadaFila(UUID obraId, UUID partidaId, BigDecimal cantidad) {
    }

    record UltimaFila(UUID obraId, LocalDate periodo, String estado) {
    }

    record LineaFila(UUID partidaId, BigDecimal cantidadPeriodo) {
    }
}
