package com.constructora.obras.saga.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Respuesta de Finanzas o Materiales a un comando de la saga (docs/contratos.md, «Eventos y comandos»). Cada una
 * corresponde a un paso y dice si salió bien o mal.
 */
public sealed interface RespuestaSaga {

    /** Paso al que responde. */
    PasoSaga paso();

    /** evento.finanzas.SaldoReservado · {planillaId, reservaId}. */
    record SaldoReservado(UUID reservaId) implements RespuestaSaga {

        public SaldoReservado {
            Objects.requireNonNull(reservaId, "reservaId");
        }

        @Override
        public PasoSaga paso() {
            return PasoSaga.RESERVAR_SALDO;
        }
    }

    /** evento.finanzas.SaldoInsuficiente · {planillaId, motivo}. */
    record SaldoInsuficiente(String motivo) implements RespuestaSaga {

        @Override
        public PasoSaga paso() {
            return PasoSaga.RESERVAR_SALDO;
        }
    }

    /** evento.materiales.ConsumosCerrados · {planillaId, obraId, periodo}. */
    record ConsumosCerrados() implements RespuestaSaga {

        @Override
        public PasoSaga paso() {
            return PasoSaga.CERRAR_CONSUMOS;
        }
    }

    /** evento.materiales.CierreFallido · {planillaId, motivo}. */
    record CierreFallido(String motivo) implements RespuestaSaga {

        public CierreFallido {
            Objects.requireNonNull(motivo, "motivo");
        }

        @Override
        public PasoSaga paso() {
            return PasoSaga.CERRAR_CONSUMOS;
        }
    }

    /** evento.finanzas.CobroEmitido · {planillaId, cobroId, numero, venceEn}. */
    record CobroEmitido(UUID cobroId, String numero) implements RespuestaSaga {

        public CobroEmitido {
            Objects.requireNonNull(cobroId, "cobroId");
            Objects.requireNonNull(numero, "numero");
        }

        @Override
        public PasoSaga paso() {
            return PasoSaga.EMITIR_COBRO;
        }
    }

    /** evento.finanzas.CobroFallido · {planillaId, motivo}. */
    record CobroFallido(String motivo) implements RespuestaSaga {

        public CobroFallido {
            Objects.requireNonNull(motivo, "motivo");
        }

        @Override
        public PasoSaga paso() {
            return PasoSaga.EMITIR_COBRO;
        }
    }
}
