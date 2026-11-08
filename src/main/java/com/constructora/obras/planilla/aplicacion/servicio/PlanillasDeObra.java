package com.constructora.obras.planilla.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.dominio.Planilla;
import java.util.UUID;

/**
 * Lo que comparten los casos de uso de planillas: cargar la obra o la planilla con la visibilidad del usuario y armar
 * el detalle. Una obra o planilla que el usuario no ve responde igual que una que no existe.
 */
final class PlanillasDeObra {

    private final ObraRepositorio obras;
    private final PlanillaRepositorio planillas;

    PlanillasDeObra(ObraRepositorio obras, PlanillaRepositorio planillas) {
        this.obras = obras;
        this.planillas = planillas;
    }

    /** Obra que el usuario ve; si no: OBRA_NO_ENCONTRADA. */
    Obra obraVisible(UUID obraId, Usuario usuario) {
        return obras.buscarPorId(obraId)
                .filter(obra -> obra.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
    }

    /** Planilla y su obra, si el usuario ve la obra; si no: PLANILLA_NO_ENCONTRADA. */
    PlanillaYObra planillaVisible(UUID planillaId, Usuario usuario) {
        Planilla planilla = planillas.buscarPorId(planillaId).orElseThrow(PlanillasDeObra::noEncontrada);
        Obra obra = obras.buscarPorId(planilla.obraId())
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(PlanillasDeObra::noEncontrada);
        return new PlanillaYObra(planilla, obra);
    }

    /** Solo el residente asignado elabora y presenta (criterio 10); con otro papel en la obra: SIN_PERMISO. */
    static void exigirResidente(Obra obra, Usuario usuario) {
        if (!obra.puedeElaborarPlanilla(usuario)) {
            throw new ReglaNegocioException("SIN_PERMISO", "Tu rol no permite esta acción.");
        }
    }

    PlanillaDetalle detalle(Planilla planilla, Obra obra) {
        return new PlanillaDetalle(planilla,
                planilla.detalle(obra, planillas.acumuladosAnteriores(obra.id(), planilla.periodo())),
                obra.contrato().anticipoPct(), obra.contrato().retencionPct());
    }

    static ReglaNegocioException noEncontrada() {
        return new ReglaNegocioException(ConsultarPlanillaCasoUso.PLANILLA_NO_ENCONTRADA, "Planilla no encontrada");
    }

    record PlanillaYObra(Planilla planilla, Obra obra) {
    }
}
