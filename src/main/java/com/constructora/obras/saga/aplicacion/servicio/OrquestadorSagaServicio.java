package com.constructora.obras.saga.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.saga.aplicacion.puerto.entrada.OrquestarSagaCasoUso;
import com.constructora.obras.saga.aplicacion.puerto.salida.SagaAprobacionRepositorio;
import com.constructora.obras.saga.dominio.Reaccion;
import com.constructora.obras.saga.dominio.RespuestaSaga;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica las respuestas de la saga (008). La saga se lee bloqueada (una respuesta y la tarea de vencimiento no la
 * cambian a la vez); la máquina de estados está en SagaAprobacion; aquí se guardan saga y planilla y se dejan en el
 * outbox los mensajes que produjo la transición, todo en la misma transacción.
 */
@Service
public class OrquestadorSagaServicio implements OrquestarSagaCasoUso {

    private static final Logger log = LoggerFactory.getLogger(OrquestadorSagaServicio.class);

    private final SagaAprobacionRepositorio sagas;
    private final PlanillaRepositorio planillas;
    private final PublicadorEventos publicador;
    private final Reloj reloj;

    public OrquestadorSagaServicio(SagaAprobacionRepositorio sagas, PlanillaRepositorio planillas,
            PublicadorEventos publicador, Reloj reloj) {
        this.sagas = sagas;
        this.planillas = planillas;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void recibir(UUID sagaId, UUID planillaId, RespuestaSaga respuesta) {
        SagaAprobacion saga = sagas.buscarParaActualizar(sagaId)
                .orElseThrow(() -> new RespuestaSagaInvalidaException("No hay una saga con el id " + sagaId));
        if (!saga.planillaId().equals(planillaId)) {
            throw new RespuestaSagaInvalidaException("La saga " + sagaId + " no es de la planilla " + planillaId);
        }
        aplicar(saga, planilla -> saga.recibir(respuesta, planilla, reloj.ahora()));
    }

    /** Ejecuta la transición y guarda lo que cambió; publica sus mensajes y registra la nota si la hay. */
    void aplicar(SagaAprobacion saga, Function<Planilla, Reaccion> transicion) {
        Planilla planilla = planillas.buscarPorId(saga.planillaId()).orElseThrow(() -> new IllegalStateException(
                "La saga " + saga.id() + " apunta a la planilla " + saga.planillaId() + ", que no existe"));
        Reaccion reaccion = transicion.apply(planilla);
        if (reaccion.planillaCambio()) {
            planillas.guardar(planilla);
        }
        if (reaccion.sagaCambio()) {
            sagas.guardar(saga);
        }
        reaccion.mensajes().forEach(publicador::publicar);
        if (reaccion.nota() != null) {
            log.info("Saga {} (planilla {}): {}", saga.id(), saga.planillaId(), reaccion.nota());
        } else {
            log.info("Saga {} (planilla {}): {} en el paso {}", saga.id(), saga.planillaId(), saga.estado(),
                    saga.paso().numero());
        }
    }
}
