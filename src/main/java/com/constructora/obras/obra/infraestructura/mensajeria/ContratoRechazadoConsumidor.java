package com.constructora.obras.obra.infraestructura.mensajeria;

import com.constructora.obras.compartido.infraestructura.mensajeria.ManejadorMensaje;
import com.constructora.obras.compartido.infraestructura.mensajeria.MensajeInvalidoException;
import com.constructora.obras.compartido.infraestructura.mensajeria.Sobre;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarRespuestaContratoCasoUso;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** evento.finanzas.ContratoRechazado · datos {obraId, motivo} → la obra queda en SIN_CONTRATO con el motivo. */
@Component
public class ContratoRechazadoConsumidor implements ManejadorMensaje {

    private final RegistrarRespuestaContratoCasoUso respuestaContrato;

    public ContratoRechazadoConsumidor(RegistrarRespuestaContratoCasoUso respuestaContrato) {
        this.respuestaContrato = respuestaContrato;
    }

    @Override
    public String tipo() {
        return "ContratoRechazado";
    }

    @Override
    public void procesar(Sobre sobre) {
        JsonNode motivo = sobre.datos().get("motivo");
        if (motivo == null || !motivo.isString() || motivo.asString().isBlank()) {
            throw new MensajeInvalidoException("Faltan datos.motivo");
        }
        respuestaContrato.contratoRechazado(ContratoRegistradoConsumidor.uuid(sobre.datos(), "obraId"),
                motivo.asString());
    }
}
