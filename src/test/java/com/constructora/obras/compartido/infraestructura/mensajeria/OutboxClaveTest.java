package com.constructora.obras.compartido.infraestructura.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.compartido.dominio.ComandoDominio;
import com.constructora.obras.compartido.dominio.EventoDominio;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 008 · Clave de enrutamiento que el outbox guarda: eventos de Obras y comandos de la saga (convenciones). */
class OutboxClaveTest {

    @Test
    void losEventosVanConEventoObrasYLosComandosConSuDestino() {
        EventoDominio evento = new EventoDominio() {
            @Override
            public String tipo() {
                return "PlanillaAprobada";
            }

            @Override
            public UUID correlacionId() {
                return UUID.randomUUID();
            }
        };
        ComandoDominio comando = new ComandoDominio() {
            @Override
            public String destino() {
                return "finanzas";
            }

            @Override
            public String tipo() {
                return "ReservarSaldo";
            }

            @Override
            public UUID correlacionId() {
                return UUID.randomUUID();
            }
        };

        assertThat(Outbox.claveDe(evento)).isEqualTo("evento.obras.PlanillaAprobada");
        assertThat(Outbox.claveDe(comando)).isEqualTo("comando.finanzas.ReservarSaldo");
    }
}
