package com.constructora.obras.saga.infraestructura.mensajeria;

import com.constructora.obras.compartido.infraestructura.mensajeria.ManejadorMensaje;
import com.constructora.obras.compartido.infraestructura.mensajeria.MensajeInvalidoException;
import com.constructora.obras.compartido.infraestructura.mensajeria.Sobre;
import com.constructora.obras.saga.aplicacion.puerto.entrada.OrquestarSagaCasoUso;
import com.constructora.obras.saga.aplicacion.puerto.entrada.OrquestarSagaCasoUso.RespuestaSagaInvalidaException;
import com.constructora.obras.saga.dominio.RespuestaSaga;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

/**
 * Consumidores de las respuestas de la saga en la cola obras (docs/contratos.md, «Eventos y comandos»): uno por tipo.
 * La saga se busca por el correlacionId del sobre (= id de la saga) y se comprueba datos.planillaId. Sin
 * correlacionId, con datos incompletos o de una saga que no existe, el mensaje va directo a plataforma.fallidos.
 */
@Configuration
public class ConsumidoresSaga {

    @Bean
    ManejadorMensaje saldoReservadoConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("SaldoReservado", saga, datos -> new RespuestaSaga.SaldoReservado(uuid(datos, "reservaId")));
    }

    /** El motivo no se usa: la planilla queda con «Saldo del contrato insuficiente» (criterio 8). */
    @Bean
    ManejadorMensaje saldoInsuficienteConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("SaldoInsuficiente", saga,
                datos -> new RespuestaSaga.SaldoInsuficiente(textoOpcional(datos, "motivo")));
    }

    @Bean
    ManejadorMensaje consumosCerradosConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("ConsumosCerrados", saga, datos -> new RespuestaSaga.ConsumosCerrados());
    }

    @Bean
    ManejadorMensaje cierreFallidoConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("CierreFallido", saga, datos -> new RespuestaSaga.CierreFallido(texto(datos, "motivo")));
    }

    @Bean
    ManejadorMensaje cobroEmitidoConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("CobroEmitido", saga,
                datos -> new RespuestaSaga.CobroEmitido(uuid(datos, "cobroId"), texto(datos, "numero")));
    }

    @Bean
    ManejadorMensaje cobroFallidoConsumidor(OrquestarSagaCasoUso saga) {
        return consumidor("CobroFallido", saga, datos -> new RespuestaSaga.CobroFallido(texto(datos, "motivo")));
    }

    private static ManejadorMensaje consumidor(String tipo, OrquestarSagaCasoUso saga,
            Function<JsonNode, RespuestaSaga> respuesta) {
        return new ManejadorMensaje() {
            @Override
            public String tipo() {
                return tipo;
            }

            @Override
            public void procesar(Sobre sobre) {
                if (sobre.correlacionId() == null) {
                    throw new MensajeInvalidoException("Falta el correlacionId (id de la saga)");
                }
                UUID planillaId = uuid(sobre.datos(), "planillaId");
                RespuestaSaga leida = respuesta.apply(sobre.datos());
                try {
                    saga.recibir(sobre.correlacionId(), planillaId, leida);
                } catch (RespuestaSagaInvalidaException error) {
                    throw new MensajeInvalidoException(error.getMessage(), error);
                }
            }
        };
    }

    static UUID uuid(JsonNode datos, String campo) {
        String valor = texto(datos, campo);
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new MensajeInvalidoException("datos." + campo + " no es un uuid");
        }
    }

    static String texto(JsonNode datos, String campo) {
        String valor = textoOpcional(datos, campo);
        if (valor == null || valor.isBlank()) {
            throw new MensajeInvalidoException("Faltan datos." + campo);
        }
        return valor;
    }

    private static String textoOpcional(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        return valor == null || !valor.isString() ? null : valor.asString();
    }
}
