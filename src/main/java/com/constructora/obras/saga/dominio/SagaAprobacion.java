package com.constructora.obras.saga.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.PlanillaAprobada;
import com.constructora.obras.planilla.dominio.PlanillaObservada;
import com.constructora.obras.saga.dominio.ComandosSaga.CerrarConsumos;
import com.constructora.obras.saga.dominio.ComandosSaga.EmitirCobro;
import com.constructora.obras.saga.dominio.ComandosSaga.LiberarReserva;
import com.constructora.obras.saga.dominio.ComandosSaga.ReabrirConsumos;
import com.constructora.obras.saga.dominio.ComandosSaga.ReservarSaldo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga «aprobar planilla», orquestada por Obras (008, diseño; docs/reglas-negocio.md, «Saga»). Guarda el paso que
 * espera respuesta, su vencimiento y lo que hace falta para compensar (reserva) o cerrar (cobro). Su id es el
 * correlacionId de todos los mensajes de la saga. Cada transición devuelve los mensajes a publicar (comandos y
 * eventos) y deja la planilla en el estado que corresponde.
 */
public final class SagaAprobacion {

    /** Cada paso espera la respuesta hasta 60 segundos (decisión del equipo; 008, criterio 11). */
    public static final Duration ESPERA_PASO = Duration.ofSeconds(60);
    /** Motivo cuando Finanzas responde SaldoInsuficiente (008, criterio 8; docs/mensajes.md). */
    public static final String SALDO_INSUFICIENTE = "Saldo del contrato insuficiente";

    private final UUID id;
    private final UUID planillaId;
    private PasoSaga paso;
    private EstadoSaga estado;
    private UUID reservaId;
    private UUID cobroId;
    private String cobroNumero;
    private String motivo;
    private Instant venceEn;
    private Instant actualizadaEn;

    private SagaAprobacion(UUID id, UUID planillaId, PasoSaga paso, EstadoSaga estado, UUID reservaId, UUID cobroId,
            String cobroNumero, String motivo, Instant venceEn, Instant actualizadaEn) {
        this.id = Objects.requireNonNull(id, "id");
        this.planillaId = Objects.requireNonNull(planillaId, "planillaId");
        this.paso = Objects.requireNonNull(paso, "paso");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.reservaId = reservaId;
        this.cobroId = cobroId;
        this.cobroNumero = cobroNumero;
        this.motivo = motivo;
        this.venceEn = Objects.requireNonNull(venceEn, "venceEn");
        this.actualizadaEn = Objects.requireNonNull(actualizadaEn, "actualizadaEn");
    }

    /**
     * Paso 1 (criterio 7): el cliente aprueba; la planilla pasa a APROBANDO (si no está PRESENTADA,
     * PLANILLA_NO_PRESENTADA) y empieza la saga esperando el paso 2, con ReservarSaldo por el neto de la planilla.
     */
    public static Inicio iniciar(Planilla planilla, Instant ahora) {
        planilla.aprobarPorCliente();
        SagaAprobacion saga = new SagaAprobacion(UUID.randomUUID(), planilla.id(), PasoSaga.RESERVAR_SALDO,
                EstadoSaga.EN_CURSO, null, null, null, null, ahora.plus(ESPERA_PASO), ahora);
        return new Inicio(saga, List.of(new ReservarSaldo(saga.id, planilla.id(), planilla.obraId(),
                planilla.totales().neto().valor())));
    }

    /**
     * Aplica la respuesta de un paso (criterios 7 a 10 y 12). Con la saga EN_CURSO y la respuesta del paso que espera:
     * <ul>
     * <li>SaldoReservado → guarda la reserva y envía CerrarConsumos (paso 3);</li>
     * <li>ConsumosCerrados → envía EmitirCobro con la reserva (paso 4);</li>
     * <li>CobroEmitido → planilla APROBADA con el cobro (paso 5), saga COMPLETADA y PlanillaAprobada;</li>
     * <li>SaldoInsuficiente, CierreFallido o CobroFallido → compensa lo hecho (ver fallar).</li>
     * </ul>
     * Una respuesta de otro paso, o con la saga COMPLETADA, no cambia nada (por ejemplo, un repetido con otro id). Con
     * la saga COMPENSADA es una respuesta tardía (ver tardia).
     *
     * @param planilla la planilla de la saga (se cambia aquí; el caso de uso la guarda si planillaCambio)
     */
    public Reaccion recibir(RespuestaSaga respuesta, Planilla planilla, Instant ahora) {
        verificarPlanilla(planilla);
        if (estado == EstadoSaga.COMPENSADA) {
            return tardia(respuesta, planilla);
        }
        if (estado != EstadoSaga.EN_CURSO) {
            return Reaccion.ninguna(nombre(respuesta) + " con la saga " + estado + ": no cambia nada");
        }
        if (respuesta.paso() != paso) {
            return Reaccion.ninguna(nombre(respuesta) + " del paso " + respuesta.paso().numero()
                    + " con la saga esperando el paso " + paso.numero() + ": no cambia nada");
        }
        return switch (respuesta) {
            case RespuestaSaga.SaldoReservado reservado -> {
                reservaId = reservado.reservaId();
                yield avanzar(PasoSaga.CERRAR_CONSUMOS, ahora,
                        new CerrarConsumos(id, planillaId, planilla.obraId(), planilla.periodo().toString()));
            }
            case RespuestaSaga.ConsumosCerrados cerrados -> avanzar(PasoSaga.EMITIR_COBRO, ahora,
                    new EmitirCobro(id, planillaId, reservaId, planilla.totales().neto().valor()));
            case RespuestaSaga.CobroEmitido emitido -> completar(emitido, planilla, ahora);
            case RespuestaSaga.SaldoInsuficiente insuficiente -> fallar(SALDO_INSUFICIENTE, planilla, ahora);
            case RespuestaSaga.CierreFallido fallido -> fallar(fallido.motivo(), planilla, ahora);
            case RespuestaSaga.CobroFallido fallido -> fallar(fallido.motivo(), planilla, ahora);
        };
    }

    /**
     * Vencimiento (criterio 11): si la saga sigue EN_CURSO y el plazo del paso actual ya pasó, se trata como un fallo
     * de ese paso con el motivo «El paso X no respondió a tiempo» y se compensa lo hecho. Si no, no cambia nada (otra
     * respuesta llegó antes).
     */
    public Reaccion vencer(Planilla planilla, Instant ahora) {
        verificarPlanilla(planilla);
        if (estado != EstadoSaga.EN_CURSO || !venceEn.isBefore(ahora)) {
            return Reaccion.ninguna("Sin vencer: saga " + estado + ", vence " + venceEn);
        }
        return fallar("El paso " + paso.numero() + " no respondió a tiempo", planilla, ahora);
    }

    /**
     * Respuesta que llega con la saga ya COMPENSADA (por ejemplo, después de vencer el paso): lo que el otro servicio
     * hizo se deshace sin cambiar la saga ni la planilla. SaldoReservado → LiberarReserva de esa reserva;
     * ConsumosCerrados → ReabrirConsumos; CobroEmitido → incidente en el registro (no debería pasar: el paso 4 solo se
     * envía tras el 3). Los fallos tardíos no cambian nada.
     */
    private Reaccion tardia(RespuestaSaga respuesta, Planilla planilla) {
        return switch (respuesta) {
            case RespuestaSaga.SaldoReservado reservado -> Reaccion.compensacionTardia(
                    new LiberarReserva(id, planillaId, reservado.reservaId()),
                    "SaldoReservado tardío con la saga compensada: se libera la reserva " + reservado.reservaId());
            case RespuestaSaga.ConsumosCerrados cerrados -> Reaccion.compensacionTardia(
                    new ReabrirConsumos(id, planillaId, planilla.obraId(), planilla.periodo().toString()),
                    "ConsumosCerrados tardío con la saga compensada: se reabren los consumos de " + planilla.periodo());
            case RespuestaSaga.CobroEmitido emitido -> Reaccion.incidente("CobroEmitido " + emitido.numero() + " ("
                    + emitido.cobroId() + ") con la saga compensada: la planilla sigue OBSERVADA; revisar el cobro");
            default -> Reaccion.ninguna(nombre(respuesta) + " tardío con la saga compensada: no cambia nada");
        };
    }

    private Reaccion avanzar(PasoSaga siguiente, Instant ahora, EventoDominio comando) {
        paso = siguiente;
        venceEn = ahora.plus(ESPERA_PASO);
        actualizadaEn = ahora;
        return Reaccion.de(List.of(comando), true, false);
    }

    /** Paso 5: la planilla queda APROBADA con el número de cobro y se publica PlanillaAprobada. */
    private Reaccion completar(RespuestaSaga.CobroEmitido emitido, Planilla planilla, Instant ahora) {
        cobroId = emitido.cobroId();
        cobroNumero = emitido.numero();
        estado = EstadoSaga.COMPLETADA;
        actualizadaEn = ahora;
        planilla.marcarAprobada(cobroId, cobroNumero);
        return Reaccion.de(List.of(PlanillaAprobada.de(id, planilla)), true, true);
    }

    /**
     * Falló el paso actual: se envían las compensaciones de los pasos ya hechos, en orden inverso y sin esperar su
     * respuesta (son idempotentes y el outbox las reintenta): fallo en el paso 3 → LiberarReserva; en el paso 4 →
     * ReabrirConsumos y LiberarReserva. La planilla queda OBSERVADA con el motivo, la saga COMPENSADA y se publica
     * PlanillaObservada con origen SAGA.
     */
    private Reaccion fallar(String motivoFallo, Planilla planilla, Instant ahora) {
        List<EventoDominio> mensajes = new ArrayList<>();
        if (paso == PasoSaga.EMITIR_COBRO) {
            mensajes.add(new ReabrirConsumos(id, planillaId, planilla.obraId(), planilla.periodo().toString()));
        }
        if (paso == PasoSaga.EMITIR_COBRO || paso == PasoSaga.CERRAR_CONSUMOS) {
            mensajes.add(new LiberarReserva(id, planillaId, reservaId));
        }
        motivo = motivoFallo;
        estado = EstadoSaga.COMPENSADA;
        actualizadaEn = ahora;
        planilla.observarPorSaga(motivoFallo);
        mensajes.add(PlanillaObservada.porSaga(id, planilla));
        return Reaccion.de(mensajes, true, true);
    }

    private void verificarPlanilla(Planilla planilla) {
        if (!planilla.id().equals(planillaId)) {
            throw new IllegalArgumentException("La planilla no es la de la saga");
        }
    }

    private static String nombre(RespuestaSaga respuesta) {
        return respuesta.getClass().getSimpleName();
    }

    /** Vuelve a armar una saga guardada (persistencia). No valida reglas. */
    public static SagaAprobacion reconstruir(UUID id, UUID planillaId, PasoSaga paso, EstadoSaga estado,
            UUID reservaId, UUID cobroId, String cobroNumero, String motivo, Instant venceEn, Instant actualizadaEn) {
        return new SagaAprobacion(id, planillaId, paso, estado, reservaId, cobroId, cobroNumero, motivo, venceEn,
                actualizadaEn);
    }

    public UUID id() {
        return id;
    }

    public UUID planillaId() {
        return planillaId;
    }

    public PasoSaga paso() {
        return paso;
    }

    public EstadoSaga estado() {
        return estado;
    }

    public UUID reservaId() {
        return reservaId;
    }

    public UUID cobroId() {
        return cobroId;
    }

    public String cobroNumero() {
        return cobroNumero;
    }

    /** Motivo del fallo, si la saga se compensó. */
    public String motivo() {
        return motivo;
    }

    /** Límite para la respuesta del paso actual. */
    public Instant venceEn() {
        return venceEn;
    }

    public Instant actualizadaEn() {
        return actualizadaEn;
    }

    /** La saga recién creada y el comando del paso 2. */
    public record Inicio(SagaAprobacion saga, List<EventoDominio> mensajes) {

        public Inicio {
            mensajes = List.copyOf(mensajes);
        }
    }
}
