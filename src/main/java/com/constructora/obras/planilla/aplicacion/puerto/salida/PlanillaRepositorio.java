package com.constructora.obras.planilla.aplicacion.puerto.salida;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.planilla.dominio.Planilla;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Planillas de avance (007): crear, guardar y leer. */
public interface PlanillaRepositorio {

    Optional<Planilla> buscarPorId(UUID id);

    /**
     * Planillas de la obra por número.
     *
     * @param incluirBorradores false: sin las que están en BORRADOR (el cliente no las ve)
     */
    List<Planilla> listarDeObra(UUID obraId, boolean incluirBorradores);

    /** La obra tiene una planilla que no está APROBADA. */
    boolean hayAbierta(UUID obraId);

    /** Periodo de la última planilla de la obra, si tiene. */
    Optional<Periodo> periodoUltima(UUID obraId);

    /** Número de la planilla siguiente de la obra (1 si no tiene). */
    int siguienteNumero(UUID obraId);

    /**
     * Acumulado anterior de cada partida: suma de cantidad_periodo en las planillas APROBADA de la obra con periodo
     * anterior al indicado (docs/reglas-negocio.md, «Planilla de avance»).
     *
     * @return partidaId → cantidad; las partidas sin planillas aprobadas no figuran
     */
    Map<UUID, BigDecimal> acumuladosAnteriores(UUID obraId, Periodo periodo);

    /**
     * Guarda una planilla nueva o los cambios de una existente y devuelve cómo quedó (con su versión nueva). Si
     * otra transacción la guardó antes, VERSION_DESACTUALIZADA; si otra creó a la vez una planilla con el mismo
     * número o periodo, PLANILLA_ABIERTA_EXISTE.
     */
    Planilla guardar(Planilla planilla);
}
