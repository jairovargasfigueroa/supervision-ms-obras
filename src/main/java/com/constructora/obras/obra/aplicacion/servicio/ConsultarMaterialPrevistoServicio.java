package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Material previsto del periodo: planilla del periodo (cualquier estado) × cantidad por unidad de los insumos. */
@Service
public class ConsultarMaterialPrevistoServicio implements ConsultarMaterialPrevistoCasoUso {

    private final ObraRepositorio obras;
    private final PlanillasObraRepositorio planillas;

    public ConsultarMaterialPrevistoServicio(ObraRepositorio obras, PlanillasObraRepositorio planillas) {
        this.obras = obras;
        this.planillas = planillas;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado consultar(UUID obraId, Periodo periodo, Usuario usuario) {
        Obra obra = obras.buscarPorId(obraId)
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        if (!obra.puedeVerMaterialPrevisto(usuario)) {
            throw new ReglaNegocioException("SIN_PERMISO", "Tu rol no permite esta acción.");
        }
        return planillas.cantidadesDelPeriodo(obra.id(), periodo)
                .map(cantidades -> new Resultado(periodo, true, obra.materialPrevisto(cantidades)))
                .orElseGet(() -> new Resultado(periodo, false, List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Resultado> consultar(Collection<UUID> obraIds, Periodo periodo, Usuario usuario) {
        List<Obra> permitidas = obras.buscarPorIds(obraIds).stream()
                .filter(obra -> obra.esVisiblePara(usuario) && obra.puedeVerMaterialPrevisto(usuario))
                .toList();
        Map<UUID, Map<UUID, BigDecimal>> cantidades = planillas.cantidadesDelPeriodo(
                permitidas.stream().map(Obra::id).toList(), periodo);
        Map<UUID, Resultado> resultados = new HashMap<>();
        for (Obra obra : permitidas) {
            Map<UUID, BigDecimal> delPeriodo = cantidades.get(obra.id());
            resultados.put(obra.id(), delPeriodo == null ? new Resultado(periodo, false, List.of())
                    : new Resultado(periodo, true, obra.materialPrevisto(delPeriodo)));
        }
        return resultados;
    }
}
