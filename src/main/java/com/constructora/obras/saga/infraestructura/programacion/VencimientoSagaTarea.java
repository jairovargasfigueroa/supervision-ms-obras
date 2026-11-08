package com.constructora.obras.saga.infraestructura.programacion;

import com.constructora.obras.saga.aplicacion.puerto.entrada.OrquestarSagaCasoUso;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada 10 s (008, diseño) busca las sagas EN_CURSO con el plazo del paso vencido y las trata como fallo con «El paso X
 * no respondió a tiempo». Cada saga se vence en su propia transacción, bloqueada: si la respuesta llega a la vez, la
 * que entra segunda ya la ve resuelta. Si una falla, se sigue con las demás y se reintenta en la vuelta siguiente.
 *
 * <p>Con obras.saga.vencimiento-activo = false (perfil semilla, 011) no se crea.
 */
@Component
@ConditionalOnBooleanProperty(name = "obras.saga.vencimiento-activo", matchIfMissing = true)
public class VencimientoSagaTarea {

    private static final Logger log = LoggerFactory.getLogger(VencimientoSagaTarea.class);

    private final OrquestarSagaCasoUso saga;

    public VencimientoSagaTarea(OrquestarSagaCasoUso saga) {
        this.saga = saga;
    }

    @Scheduled(fixedDelayString = "${obras.saga.intervalo-vencimiento-ms:10000}")
    public void vencerPasos() {
        for (UUID sagaId : saga.sagasVencidas()) {
            try {
                saga.vencer(sagaId);
            } catch (RuntimeException error) {
                log.warn("No se pudo vencer la saga {}: {}", sagaId, error.toString());
            }
        }
    }
}
