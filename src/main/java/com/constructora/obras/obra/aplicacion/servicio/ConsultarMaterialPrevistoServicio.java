package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.util.List;
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
}
