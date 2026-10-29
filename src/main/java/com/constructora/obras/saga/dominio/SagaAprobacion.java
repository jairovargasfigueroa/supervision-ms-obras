package com.constructora.obras.saga.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.saga.dominio.ComandosSaga.ReservarSaldo;
import java.time.Duration;
import java.time.Instant;
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
