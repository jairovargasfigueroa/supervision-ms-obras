package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.dominio.MaterialPrevisto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 006 · Material previsto de un periodo (pestaña «Material» del detalle): a partir de la planilla del periodo, en
 * cualquier estado, y del material previsto por unidad de cada partida.
 */
public interface ConsultarMaterialPrevistoCasoUso {

    /**
     * Si la obra no existe o el usuario no la ve: OBRA_NO_ENCONTRADA. Si la ve pero no es admin, gerente ni el
     * residente asignado (por ejemplo, la ve como maestro): SIN_PERMISO.
     */
    Resultado consultar(UUID obraId, Periodo periodo, Usuario usuario);

    /**
     * 505 (avance_obra por lote) · Material previsto del periodo de varias obras, con una sola lectura de las
     * planillas. Las obras que no existen, que el usuario no ve o cuyo material previsto no puede ver no figuran.
     *
     * @return obraId → resultado, con el mismo cálculo que {@link #consultar(UUID, Periodo, Usuario)}
     */
    Map<UUID, Resultado> consultar(Collection<UUID> obraIds, Periodo periodo, Usuario usuario);

    /** Sin planilla en el periodo: hayPlanilla es false e items está vacío («Sin planilla en este periodo»). */
    record Resultado(Periodo periodo, boolean hayPlanilla, List<MaterialPrevisto> items) {

        public Resultado {
            items = List.copyOf(items);
        }
    }
}
