package com.constructora.obras.saga.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.servicio.RevisarPlanillaServicio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.PlanillaAprobada;
import com.constructora.obras.planilla.dominio.PlanillaObservada;
import com.constructora.obras.saga.aplicacion.DoblesSaga.BusSimulado;
import com.constructora.obras.saga.aplicacion.DoblesSaga.ObrasEnMemoria;
import com.constructora.obras.saga.aplicacion.DoblesSaga.PlanillasEnMemoria;
import com.constructora.obras.saga.aplicacion.DoblesSaga.RelojManual;
import com.constructora.obras.saga.aplicacion.DoblesSaga.SagasEnMemoria;
import com.constructora.obras.saga.aplicacion.puerto.entrada.OrquestarSagaCasoUso.RespuestaSagaInvalidaException;
import com.constructora.obras.saga.aplicacion.servicio.OrquestadorSagaServicio;
import com.constructora.obras.saga.dominio.ComandosSaga.CerrarConsumos;
import com.constructora.obras.saga.dominio.ComandosSaga.EmitirCobro;
import com.constructora.obras.saga.dominio.ComandosSaga.LiberarReserva;
import com.constructora.obras.saga.dominio.ComandosSaga.ReabrirConsumos;
import com.constructora.obras.saga.dominio.EstadoSaga;
import com.constructora.obras.saga.dominio.PasoSaga;
import com.constructora.obras.saga.dominio.RespuestaSaga;
import com.constructora.obras.saga.dominio.RespuestaSaga.CierreFallido;
import com.constructora.obras.saga.dominio.RespuestaSaga.CobroEmitido;
import com.constructora.obras.saga.dominio.RespuestaSaga.CobroFallido;
import com.constructora.obras.saga.dominio.RespuestaSaga.ConsumosCerrados;
import com.constructora.obras.saga.dominio.RespuestaSaga.SaldoInsuficiente;
import com.constructora.obras.saga.dominio.RespuestaSaga.SaldoReservado;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 008 · Máquina de estados de la saga «aprobar planilla» con un bus simulado (diseño, «Pruebas»): camino feliz, fallo
 * en cada paso con sus compensaciones, vencimiento por tiempo, respuestas repetidas y respuestas tardías tras compensar.
 * Planilla de 4 m2 de losa × 100 (subtotal 400, neto 332,00) del periodo 2026-10.
 */
class OrquestadorSagaServicioTest {

    private final Obra obra = DoblesSaga.obra();
    private final Usuario cliente = new Usuario(obra.asignaciones().clienteId().toString(), Set.of("cliente"));
    private final RelojManual reloj = new RelojManual();
    private final PlanillasEnMemoria planillas = new PlanillasEnMemoria();
    private final SagasEnMemoria sagas = new SagasEnMemoria();
    private final BusSimulado bus = new BusSimulado();
    private final RevisarPlanillaServicio revisar = new RevisarPlanillaServicio(new ObrasEnMemoria(obra), planillas,
            sagas, bus, reloj);
    private final OrquestadorSagaServicio orquestador = new OrquestadorSagaServicio(sagas, planillas, bus, reloj);

    private final UUID reservaId = UUID.randomUUID();
    private final UUID cobroId = UUID.randomUUID();
    private Planilla planilla;
    private UUID saga;

    @BeforeEach
    void elClienteAprueba() {
        planilla = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);
        revisar.aprobar(planilla.id(), cliente);
        saga = sagas.unica().id();
        assertThat(DoblesSaga.clave(bus.tomar().getFirst())).isEqualTo("comando.finanzas.ReservarSaldo");
    }

    // --- camino feliz ---

    @Test
    void caminoFelizReservaCierraEmiteYApruebaConElCobro() {
        reloj.adelantar(Duration.ofSeconds(5));
        recibir(new SaldoReservado(reservaId));
        CerrarConsumos cerrar = solo(CerrarConsumos.class);
        assertThat(cerrar).isEqualTo(new CerrarConsumos(saga, planilla.id(), obra.id(), "2026-10"));
        assertThat(saga().paso()).isEqualTo(PasoSaga.CERRAR_CONSUMOS);
        assertThat(saga().reservaId()).isEqualTo(reservaId);
        // Cada paso tiene sus propios 60 s
        assertThat(saga().venceEn()).isEqualTo(reloj.ahora().plusSeconds(60));

        reloj.adelantar(Duration.ofSeconds(5));
        recibir(new ConsumosCerrados());
        EmitirCobro emitir = solo(EmitirCobro.class);
        assertThat(emitir.correlacionId()).isEqualTo(saga);
        assertThat(emitir.reservaId()).isEqualTo(reservaId);
        assertThat(emitir.monto()).isEqualByComparingTo("332.00");
        assertThat(saga().paso()).isEqualTo(PasoSaga.EMITIR_COBRO);
        assertThat(planillas.leer(planilla.id()).estado()).isEqualTo(EstadoPlanilla.APROBANDO);

        recibir(new CobroEmitido(cobroId, "COB-2026-0001"));
        PlanillaAprobada aprobada = solo(PlanillaAprobada.class);
        assertThat(aprobada.correlacionId()).isEqualTo(saga);
        assertThat(aprobada.datos()).isEqualTo(new PlanillaAprobada.Datos(planilla.id(), obra.id(), "2026-10",
                new BigDecimal("332.00"), cobroId, List.of(new PlanillaAprobada.Linea(
                        obra.partidas().getFirst().id(), new BigDecimal("4.000"), new BigDecimal("400.00")))));
        Planilla final_ = planillas.leer(planilla.id());
        assertThat(final_.estado()).isEqualTo(EstadoPlanilla.APROBADA);
        assertThat(final_.cobroId()).isEqualTo(cobroId);
        assertThat(final_.cobroNumero()).isEqualTo("COB-2026-0001");
        assertThat(final_.observacion()).isNull();
        assertThat(saga().estado()).isEqualTo(EstadoSaga.COMPLETADA);
        assertThat(saga().cobroNumero()).isEqualTo("COB-2026-0001");
    }

    // --- fallo en cada paso ---

    @Test
    void sinSaldoQuedaObservadaSinCompensaciones() {
        recibir(new SaldoInsuficiente("otro texto de Finanzas"));

        assertThat(bus.claves()).containsExactly("evento.obras.PlanillaObservada");
        assertObservadaPorSaga("Saldo del contrato insuficiente");
    }

    @Test
    void siFallaElCierreSeLiberaLaReserva() {
        recibir(new SaldoReservado(reservaId));
        bus.tomar();

        recibir(new CierreFallido("Los consumos del periodo 2026-10 ya fueron cerrados por otra planilla"));

        assertThat(bus.claves()).containsExactly("comando.finanzas.LiberarReserva", "evento.obras.PlanillaObservada");
        assertThat(bus.ultimo(LiberarReserva.class)).isEqualTo(new LiberarReserva(saga, planilla.id(), reservaId));
        assertObservadaPorSaga("Los consumos del periodo 2026-10 ya fueron cerrados por otra planilla");
    }

    @Test
    void siFallaElCobroSeReabrenLosConsumosYSeLiberaLaReservaEnEseOrden() {
        recibir(new SaldoReservado(reservaId));
        recibir(new ConsumosCerrados());
        bus.tomar();

        recibir(new CobroFallido("No se pudo emitir el cobro: la reserva de saldo ya no está vigente"));

        assertThat(bus.claves()).containsExactly("comando.materiales.ReabrirConsumos",
                "comando.finanzas.LiberarReserva", "evento.obras.PlanillaObservada");
        assertThat(bus.ultimo(ReabrirConsumos.class))
                .isEqualTo(new ReabrirConsumos(saga, planilla.id(), obra.id(), "2026-10"));
        assertThat(bus.ultimo(LiberarReserva.class).reservaId()).isEqualTo(reservaId);
        assertObservadaPorSaga("No se pudo emitir el cobro: la reserva de saldo ya no está vigente");
    }

    // --- vencimiento ---

    @Test
    void elPaso2VenceALos60SinRespuesta() {
        reloj.adelantar(Duration.ofSeconds(60));
        assertThat(orquestador.sagasVencidas()).isEmpty();
        orquestador.vencer(saga);
        assertThat(bus.publicados()).isEmpty();
        assertThat(saga().estado()).isEqualTo(EstadoSaga.EN_CURSO);

        reloj.adelantar(Duration.ofSeconds(1));
        assertThat(orquestador.sagasVencidas()).containsExactly(saga);
        orquestador.vencer(saga);

        assertThat(bus.claves()).containsExactly("evento.obras.PlanillaObservada");
        assertObservadaPorSaga("El paso 2 no respondió a tiempo");
        assertThat(orquestador.sagasVencidas()).isEmpty();
    }

    @Test
    void elPaso3VenceYSeLiberaLaReserva() {
        recibir(new SaldoReservado(reservaId));
        bus.tomar();
        reloj.adelantar(Duration.ofSeconds(61));

        orquestador.vencer(saga);

        assertThat(bus.claves()).containsExactly("comando.finanzas.LiberarReserva", "evento.obras.PlanillaObservada");
        assertObservadaPorSaga("El paso 3 no respondió a tiempo");
    }

    @Test
    void elPaso4VenceYSeCompensanLosDosPasosAnteriores() {
        recibir(new SaldoReservado(reservaId));
        recibir(new ConsumosCerrados());
        bus.tomar();
        reloj.adelantar(Duration.ofSeconds(61));

        orquestador.vencer(saga);
        // La tarea puede volver a pasar: no hace nada dos veces
        orquestador.vencer(saga);

        assertThat(bus.claves()).containsExactly("comando.materiales.ReabrirConsumos",
                "comando.finanzas.LiberarReserva", "evento.obras.PlanillaObservada");
        assertObservadaPorSaga("El paso 4 no respondió a tiempo");
    }

    @Test
    void unaRespuestaATiempoEvitaElVencimiento() {
        reloj.adelantar(Duration.ofSeconds(59));
        recibir(new SaldoReservado(reservaId));
        bus.tomar();
        reloj.adelantar(Duration.ofSeconds(59));

        assertThat(orquestador.sagasVencidas()).isEmpty();
        orquestador.vencer(saga);
        assertThat(bus.publicados()).isEmpty();
        assertThat(saga().estado()).isEqualTo(EstadoSaga.EN_CURSO);
    }

    // --- repetidos (criterio 12) ---

    @Test
    void unaRespuestaRepetidaConOtroIdNoAvanzaDosVeces() {
        recibir(new SaldoReservado(reservaId));
        recibir(new SaldoReservado(reservaId));
        assertThat(bus.claves()).containsExactly("comando.materiales.CerrarConsumos");

        recibir(new ConsumosCerrados());
        recibir(new ConsumosCerrados());
        // Respuesta de un paso que ya pasó
        recibir(new SaldoInsuficiente(null));
        recibir(new CobroEmitido(cobroId, "COB-2026-0001"));
        recibir(new CobroEmitido(cobroId, "COB-2026-0001"));
        recibir(new CobroFallido("No se pudo emitir el cobro por un error en Finanzas"));

        assertThat(bus.claves()).containsExactly("comando.materiales.CerrarConsumos",
                "comando.finanzas.EmitirCobro", "evento.obras.PlanillaAprobada");
        assertThat(planillas.leer(planilla.id()).estado()).isEqualTo(EstadoPlanilla.APROBADA);
        assertThat(saga().estado()).isEqualTo(EstadoSaga.COMPLETADA);
    }

    @Test
    void unaRespuestaDeUnPasoQueTodaviaNoSeEnvioNoCambiaNada() {
        recibir(new ConsumosCerrados());
        recibir(new CobroEmitido(cobroId, "COB-2026-0001"));

        assertThat(bus.publicados()).isEmpty();
        assertThat(saga().estado()).isEqualTo(EstadoSaga.EN_CURSO);
        assertThat(saga().paso()).isEqualTo(PasoSaga.RESERVAR_SALDO);
    }

    // --- respuestas tardías tras compensar ---

    @Test
    void unaReservaTardiaTrasVencerElPaso2SeLibera() {
        reloj.adelantar(Duration.ofSeconds(61));
        orquestador.vencer(saga);
        bus.tomar();

        recibir(new SaldoReservado(reservaId));

        assertThat(bus.publicados()).containsExactly(new LiberarReserva(saga, planilla.id(), reservaId));
        assertObservada("El paso 2 no respondió a tiempo");
    }

    @Test
    void unCierreTardioTrasVencerElPaso3SeReabre() {
        recibir(new SaldoReservado(reservaId));
        reloj.adelantar(Duration.ofSeconds(61));
        orquestador.vencer(saga);
        bus.tomar();

        recibir(new ConsumosCerrados());

        assertThat(bus.publicados()).containsExactly(new ReabrirConsumos(saga, planilla.id(), obra.id(), "2026-10"));
        assertObservada("El paso 3 no respondió a tiempo");
    }

    @Test
    void unCobroTardioTrasCompensarQuedaComoIncidenteSinCambiarNada() {
        recibir(new SaldoReservado(reservaId));
        recibir(new ConsumosCerrados());
        reloj.adelantar(Duration.ofSeconds(61));
        orquestador.vencer(saga);
        bus.tomar();

        recibir(new CobroEmitido(cobroId, "COB-2026-0001"));
        recibir(new CobroFallido("No se pudo emitir el cobro por un error en Finanzas"));
        recibir(new SaldoInsuficiente(null));

        assertThat(bus.publicados()).isEmpty();
        assertObservada("El paso 4 no respondió a tiempo");
        assertThat(planillas.leer(planilla.id()).cobroId()).isNull();
        assertThat(saga().cobroId()).isNull();
    }

    // --- mensajes que no son de ninguna saga ---

    @Test
    void unaSagaQueNoExisteODeOtraPlanillaEsInvalida() {
        assertThatThrownBy(() -> orquestador.recibir(UUID.randomUUID(), planilla.id(), new SaldoReservado(reservaId)))
                .isInstanceOf(RespuestaSagaInvalidaException.class);
        assertThatThrownBy(() -> orquestador.recibir(saga, UUID.randomUUID(), new SaldoReservado(reservaId)))
                .isInstanceOf(RespuestaSagaInvalidaException.class);
        assertThat(bus.publicados()).isEmpty();
        // Vencer una saga que no existe no hace nada
        orquestador.vencer(UUID.randomUUID());
    }

    // --- ayudas ---

    private void recibir(RespuestaSaga respuesta) {
        orquestador.recibir(saga, planilla.id(), respuesta);
    }

    private SagaAprobacion saga() {
        return sagas.unica();
    }

    /** El único mensaje publicado desde la última vez, del tipo indicado. */
    private <T extends EventoDominio> T solo(Class<T> tipo) {
        List<EventoDominio> publicados = bus.tomar();
        assertThat(publicados).hasSize(1);
        assertThat(publicados.getFirst()).isInstanceOf(tipo);
        return tipo.cast(publicados.getFirst());
    }

    /** Planilla OBSERVADA con el motivo y saga COMPENSADA. */
    private void assertObservada(String motivo) {
        Planilla observada = planillas.leer(planilla.id());
        assertThat(observada.estado()).isEqualTo(EstadoPlanilla.OBSERVADA);
        assertThat(observada.observacion()).isEqualTo(motivo);
        assertThat(saga().estado()).isEqualTo(EstadoSaga.COMPENSADA);
        assertThat(saga().motivo()).isEqualTo(motivo);
    }

    /** Como assertObservada y, además, se publicó PlanillaObservada con origen SAGA. */
    private void assertObservadaPorSaga(String motivo) {
        assertObservada(motivo);
        PlanillaObservada evento = bus.ultimo(PlanillaObservada.class);
        assertThat(evento.correlacionId()).isEqualTo(saga);
        assertThat(evento.datos()).isEqualTo(new PlanillaObservada.Datos(planilla.id(), obra.id(), motivo, "SAGA"));
    }
}
