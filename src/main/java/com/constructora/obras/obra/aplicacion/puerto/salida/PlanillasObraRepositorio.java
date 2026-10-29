package com.constructora.obras.obra.aplicacion.puerto.salida;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.planilla.dominio.PlanillaCorta;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la consulta de obras (006) lee de las planillas de avance: solo lectura. Crear, guardar y presentar
 * planillas es de la 007.
 */
public interface PlanillasObraRepositorio {

    /**
     * Cantidad ejecutada acumulada por partida: suma de cantidad_periodo de las líneas en planillas APROBADA
     * (docs/reglas-negocio.md, «Avance y plazo»).
     *
     * @return obraId → (partidaId → cantidad); las obras sin planillas aprobadas no figuran
     */
    Map<UUID, Map<UUID, BigDecimal>> ejecutadaAcumulada(Collection<UUID> obraIds);

    /**
     * Planilla de periodo más reciente de cada obra, en cualquier estado.
     *
     * @return obraId → periodo y estado; las obras sin planillas no figuran
     */
    Map<UUID, PlanillaCorta> ultimas(Collection<UUID> obraIds);

    /**
     * Cantidad del periodo de cada partida en la planilla de ese periodo (en cualquier estado).
     *
     * @return partidaId → cantidad_periodo, o vacío si la obra no tiene planilla en ese periodo
     */
    Optional<Map<UUID, BigDecimal>> cantidadesDelPeriodo(UUID obraId, Periodo periodo);
}
