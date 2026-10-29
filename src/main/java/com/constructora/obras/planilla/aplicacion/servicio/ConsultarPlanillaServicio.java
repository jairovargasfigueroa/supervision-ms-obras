package com.constructora.obras.planilla.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.aplicacion.servicio.PlanillasDeObra.PlanillaYObra;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista y detalle de planillas (007): los ven quienes ven la obra; el cliente, sin los BORRADOR. */
@Service
public class ConsultarPlanillaServicio implements ConsultarPlanillaCasoUso {

    private final PlanillaRepositorio planillas;
    private final PlanillasDeObra comunes;

    public ConsultarPlanillaServicio(ObraRepositorio obras, PlanillaRepositorio planillas) {
        this.planillas = planillas;
        this.comunes = new PlanillasDeObra(obras, planillas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Planilla> listar(UUID obraId, Usuario usuario) {
        Obra obra = comunes.obraVisible(obraId, usuario);
        return planillas.listarDeObra(obra.id(), !obra.veSoloComoCliente(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanillaDetalle obtener(UUID planillaId, Usuario usuario) {
        PlanillaYObra encontrada = comunes.planillaVisible(planillaId, usuario);
        if (encontrada.planilla().estado() == EstadoPlanilla.BORRADOR && encontrada.obra().veSoloComoCliente(usuario)) {
            throw PlanillasDeObra.noEncontrada();
        }
        return comunes.detalle(encontrada.planilla(), encontrada.obra());
    }
}
