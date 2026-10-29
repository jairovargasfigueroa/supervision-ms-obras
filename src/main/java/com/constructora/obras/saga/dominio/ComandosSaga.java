package com.constructora.obras.saga.dominio;

import com.constructora.obras.compartido.dominio.ComandoDominio;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Comandos que Obras envía en la saga «aprobar planilla» (docs/contratos.md, «Eventos y comandos»). El correlacionId
 * es el id de la saga; datos() devuelve exactamente los campos del contrato. Montos con 2 decimales y periodo AAAA-MM.
 */
public final class ComandosSaga {

    public static final String FINANZAS = "finanzas";
    public static final String MATERIALES = "materiales";

    private ComandosSaga() {
    }

    /** comando.finanzas.ReservarSaldo · {planillaId, obraId, monto}: paso 2, monto = neto de la planilla. */
    public record ReservarSaldo(UUID correlacionId, UUID planillaId, UUID obraId, BigDecimal monto)
            implements ComandoDominio {

        public ReservarSaldo {
            Objects.requireNonNull(correlacionId, "correlacionId");
        }

        @Override
        public String tipo() {
            return "ReservarSaldo";
        }

        @Override
        public String destino() {
            return FINANZAS;
        }

        @Override
        public Object datos() {
            return new Datos(planillaId, obraId, monto);
        }

        public record Datos(UUID planillaId, UUID obraId, BigDecimal monto) {
        }
    }

    /** comando.materiales.CerrarConsumos · {planillaId, obraId, periodo}: paso 3. */
    public record CerrarConsumos(UUID correlacionId, UUID planillaId, UUID obraId, String periodo)
            implements ComandoDominio {

        public CerrarConsumos {
            Objects.requireNonNull(correlacionId, "correlacionId");
        }

        @Override
        public String tipo() {
            return "CerrarConsumos";
        }

        @Override
        public String destino() {
            return MATERIALES;
        }

        @Override
        public Object datos() {
            return new ConsumosDatos(planillaId, obraId, periodo);
        }
    }

    /** comando.finanzas.EmitirCobro · {planillaId, reservaId, monto}: paso 4. */
    public record EmitirCobro(UUID correlacionId, UUID planillaId, UUID reservaId, BigDecimal monto)
            implements ComandoDominio {

        public EmitirCobro {
            Objects.requireNonNull(correlacionId, "correlacionId");
        }

        @Override
        public String tipo() {
            return "EmitirCobro";
        }

        @Override
        public String destino() {
            return FINANZAS;
        }

        @Override
        public Object datos() {
            return new Datos(planillaId, reservaId, monto);
        }

        public record Datos(UUID planillaId, UUID reservaId, BigDecimal monto) {
        }
    }

    /** comando.finanzas.LiberarReserva · {planillaId, reservaId}: compensa el paso 2. */
    public record LiberarReserva(UUID correlacionId, UUID planillaId, UUID reservaId) implements ComandoDominio {

        public LiberarReserva {
            Objects.requireNonNull(correlacionId, "correlacionId");
        }

        @Override
        public String tipo() {
            return "LiberarReserva";
        }

        @Override
        public String destino() {
            return FINANZAS;
        }

        @Override
        public Object datos() {
            return new Datos(planillaId, reservaId);
        }

        public record Datos(UUID planillaId, UUID reservaId) {
        }
    }

    /** comando.materiales.ReabrirConsumos · {planillaId, obraId, periodo}: compensa el paso 3. */
    public record ReabrirConsumos(UUID correlacionId, UUID planillaId, UUID obraId, String periodo)
            implements ComandoDominio {

        public ReabrirConsumos {
            Objects.requireNonNull(correlacionId, "correlacionId");
        }

        @Override
        public String tipo() {
            return "ReabrirConsumos";
        }

        @Override
        public String destino() {
            return MATERIALES;
        }

        @Override
        public Object datos() {
            return new ConsumosDatos(planillaId, obraId, periodo);
        }
    }

    /** Datos de CerrarConsumos y ReabrirConsumos. */
    public record ConsumosDatos(UUID planillaId, UUID obraId, String periodo) {
    }
}
