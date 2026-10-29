package com.constructora.obras.planilla.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.RevisarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.aplicacion.servicio.PlanillasDeObra.PlanillaYObra;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.PlanillaObservada;
import com.constructora.obras.saga.aplicacion.puerto.salida.SagaAprobacionRepositorio;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobar u observar una planilla desde el portal (008). Orden: planilla visible (404) → BORRADOR visto solo como
 * cliente (404, como en GET /planillas/{id}) → cliente de la obra (403) → PRESENTADA (409) → motivo (422, solo al
 * observar). Los mensajes se guardan en el outbox en la misma transacción.
 */
@Service
public class RevisarPlanillaServicio implements RevisarPlanillaCasoUso {

    private final PlanillaRepositorio planillas;
    private final SagaAprobacionRepositorio sagas;
    private final PublicadorEventos publicador;
    private final Reloj reloj;
    private final PlanillasDeObra comunes;

    public RevisarPlanillaServicio(ObraRepositorio obras, PlanillaRepositorio planillas,
            SagaAprobacionRepositorio sagas, PublicadorEventos publicador, Reloj reloj) {
        this.planillas = planillas;
        this.sagas = sagas;
        this.publicador = publicador;
        this.reloj = reloj;
        this.comunes = new PlanillasDeObra(obras, planillas);
    }

    @Override
    @Transactional
    public EstadoPlanilla aprobar(UUID planillaId, Usuario usuario) {
        Planilla planilla = planillaDelCliente(planillaId, usuario).planilla();
        SagaAprobacion.Inicio inicio = SagaAprobacion.iniciar(planilla, reloj.ahora());
        Planilla guardada = guardarRevisada(planilla);
        sagas.guardar(inicio.saga());
        inicio.mensajes().forEach(publicador::publicar);
        return guardada.estado();
    }

    @Override
    @Transactional
    public PlanillaDetalle observar(UUID planillaId, String motivo, Usuario usuario) {
        PlanillaYObra encontrada = planillaDelCliente(planillaId, usuario);
        Planilla planilla = encontrada.planilla();
        planilla.observarPorCliente(motivo);
        Planilla guardada = guardarRevisada(planilla);
        publicador.publicar(PlanillaObservada.porCliente(guardada));
        return comunes.detalle(guardada, encontrada.obra());
    }

    private PlanillaYObra planillaDelCliente(UUID planillaId, Usuario usuario) {
        PlanillaYObra encontrada = comunes.planillaVisible(planillaId, usuario);
        Obra obra = encontrada.obra();
        if (encontrada.planilla().estado() == EstadoPlanilla.BORRADOR && obra.veSoloComoCliente(usuario)) {
            throw PlanillasDeObra.noEncontrada();
        }
        if (!obra.puedeRevisarPlanilla(usuario)) {
            throw new ReglaNegocioException("SIN_PERMISO", "Tu rol no permite esta acción.");
        }
        return encontrada;
    }

    /**
     * Si otra petición la revisó a la vez (aprobar dos veces, aprobar y observar), la versión ya no coincide: para el
     * cliente la planilla ya no está esperando su revisión.
     */
    private Planilla guardarRevisada(Planilla planilla) {
        try {
            return planillas.guardar(planilla);
        } catch (ReglaNegocioException error) {
            if (Planilla.VERSION_DESACTUALIZADA.equals(error.codigo())) {
                throw new ReglaNegocioException(Planilla.PLANILLA_NO_PRESENTADA,
                        "Esta planilla ya no está esperando tu revisión. Recarga para ver su estado actual.");
            }
            throw error;
        }
    }
}
