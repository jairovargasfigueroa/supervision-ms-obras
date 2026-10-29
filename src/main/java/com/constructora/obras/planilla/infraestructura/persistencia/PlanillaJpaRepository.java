package com.constructora.obras.planilla.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Planillas: lectura de la 006 (avance, última planilla, material previsto) y, desde la 007, crear y guardar. */
public interface PlanillaJpaRepository extends JpaRepository<PlanillaEntity, UUID> {

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

    /**
     * Como buscarUltimas, pero sin contar los BORRADOR: lo que ve el cliente desde el portal (007, la lista de
     * planillas tampoco le muestra borradores).
     */
    @Query("""
            select new com.constructora.obras.planilla.infraestructura.persistencia.PlanillaJpaRepository$UltimaFila(
                p.obraId, p.periodo, p.estado)
            from PlanillaEntity p
            where p.obraId in :obraIds and p.estado <> 'BORRADOR'
              and p.periodo = (select max(q.periodo) from PlanillaEntity q
                               where q.obraId = p.obraId and q.estado <> 'BORRADOR')""")
    List<UltimaFila> buscarUltimasSinBorrador(@Param("obraIds") Collection<UUID> obraIds);

    boolean existsByObraIdAndPeriodo(UUID obraId, LocalDate periodo);

    /** 007 · Hay una planilla sin aprobar en la obra. */
    boolean existsByObraIdAndEstadoNot(UUID obraId, String estado);

    /** 007 · Periodo (primer día) de la última planilla de la obra. */
    @Query("select max(p.periodo) from PlanillaEntity p where p.obraId = :obraId")
    Optional<LocalDate> buscarUltimoPeriodo(@Param("obraId") UUID obraId);

    /** 007 · Mayor número de planilla de la obra (0 si no tiene). */
    @Query("select coalesce(max(p.numero), 0) from PlanillaEntity p where p.obraId = :obraId")
    int buscarMayorNumero(@Param("obraId") UUID obraId);

    /** 007 · Suma de cantidad_periodo por partida en las planillas APROBADA de la obra anteriores al periodo. */
    @Query("""
            select new com.constructora.obras.planilla.infraestructura.persistencia.PlanillaJpaRepository$LineaFila(
                l.partidaId, sum(l.cantidadPeriodo))
            from PlanillaEntity p join p.lineas l
            where p.obraId = :obraId and p.estado = 'APROBADA' and p.periodo < :periodo
            group by l.partidaId""")
    List<LineaFila> sumarAprobadasAnteriores(@Param("obraId") UUID obraId, @Param("periodo") LocalDate periodo);

    /** 007 · Planillas de la obra por número. */
    List<PlanillaEntity> findByObraIdOrderByNumeroAsc(UUID obraId);

    /** 007 · Planillas de la obra por número, sin las de un estado (el cliente no ve BORRADOR). */
    List<PlanillaEntity> findByObraIdAndEstadoNotOrderByNumeroAsc(UUID obraId, String estado);

    record EjecutadaFila(UUID obraId, UUID partidaId, BigDecimal cantidad) {
    }

    record UltimaFila(UUID obraId, LocalDate periodo, String estado) {
    }

    record LineaFila(UUID partidaId, BigDecimal cantidadPeriodo) {
    }
}
