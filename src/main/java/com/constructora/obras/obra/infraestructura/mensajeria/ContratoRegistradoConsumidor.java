package com.constructora.obras.obra.infraestructura.mensajeria;

import com.constructora.obras.compartido.infraestructura.mensajeria.ManejadorMensaje;
import com.constructora.obras.compartido.infraestructura.mensajeria.MensajeInvalidoException;
import com.constructora.obras.compartido.infraestructura.mensajeria.Sobre;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarRespuestaContratoCasoUso;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** evento.finanzas.ContratoRegistrado · datos {obraId, contratoId} → la obra pasa a EN_EJECUCION. */
@Component
public class ContratoRegistradoConsumidor implements ManejadorMensaje {

    private final RegistrarRespuestaContratoCasoUso respuestaContrato;

    public ContratoRegistradoConsumidor(RegistrarRespuestaContratoCasoUso respuestaContrato) {
        this.respuestaContrato = respuestaContrato;
    }

    @Override
    public String tipo() {
        return "ContratoRegistrado";
    }

    @Override
    public void procesar(Sobre sobre) {
        respuestaContrato.contratoRegistrado(uuid(sobre.datos(), "obraId"), uuid(sobre.datos(), "contratoId"));
    }

    /** Campo uuid obligatorio de los datos; si falta o no es un uuid, el mensaje es inválido. */
    static UUID uuid(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        if (valor == null || !valor.isString()) {
            throw new MensajeInvalidoException("Faltan datos." + campo);
        }
        try {
            return UUID.fromString(valor.asString());
        } catch (IllegalArgumentException e) {
            throw new MensajeInvalidoException("datos." + campo + " no es un uuid");
        }
    }
}
