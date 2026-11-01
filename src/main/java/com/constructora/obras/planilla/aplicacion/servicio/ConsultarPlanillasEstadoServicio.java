package com.constructora.obras.planilla.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillasEstadoRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 501 · Planillas por estado de todas las obras (o de una), para quien ve todas las obras. */
@Service
public class ConsultarPlanillasEstadoServicio implements ConsultarPlanillasEstadoCasoUso {

    private final ObraRepositorio obras;
    private final PlanillasEstadoRepositorio planillas;

    public ConsultarPlanillasEstadoServicio(ObraRepositorio obras, PlanillasEstadoRepositorio planillas) {
        this.obras = obras;
        this.planillas = planillas;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado consultar(Filtro filtro, Usuario usuario) {
        if (!Obra.veTodas(usuario)) {
            throw new ReglaNegocioException("SIN_PERMISO", "Tu rol no permite esta acción.");
        }
        if (filtro.obraId() != null && obras.buscarPorId(filtro.obraId()).isEmpty()) {
            throw new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "Obra no encontrada");
        }
        List<PlanillaEstado> encontradas = planillas.buscar(filtro, filtro.limite() + 1);
        boolean hayMas = encontradas.size() > filtro.limite();
        return new Resultado(hayMas ? encontradas.subList(0, filtro.limite()) : encontradas, hayMas);
    }
}
