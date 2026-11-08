package com.constructora.obras.planilla.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.servicio.RevisarPlanillaServicio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.PlanillaObservada;
import com.constructora.obras.saga.aplicacion.DoblesSaga;
import com.constructora.obras.saga.aplicacion.DoblesSaga.BusSimulado;
import com.constructora.obras.saga.aplicacion.DoblesSaga.ObrasEnMemoria;
import com.constructora.obras.saga.aplicacion.DoblesSaga.PlanillasEnMemoria;
import com.constructora.obras.saga.aplicacion.DoblesSaga.RelojManual;
import com.constructora.obras.saga.aplicacion.DoblesSaga.SagasEnMemoria;
import com.constructora.obras.saga.dominio.ComandosSaga.ReservarSaldo;
import com.constructora.obras.saga.dominio.EstadoSaga;
import com.constructora.obras.saga.dominio.PasoSaga;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 008 · El cliente aprueba u observa la planilla (criterios 3 a 5 y 7, paso 1) con dobles a mano de los puertos. */
class RevisarPlanillaServicioTest {

    private final Obra obra = DoblesSaga.obra();
    private final Usuario cliente = new Usuario(obra.asignaciones().clienteId().toString(), Set.of("cliente"));
    private final Usuario otroCliente = new Usuario("otro-cliente", Set.of("cliente"));
    private final Usuario gerenteYCliente = new Usuario("gerente-cliente", Set.of("gerente", "cliente"));

    private final RelojManual reloj = new RelojManual();
    private final PlanillasEnMemoria planillas = new PlanillasEnMemoria();
    private final SagasEnMemoria sagas = new SagasEnMemoria();
    private final BusSimulado bus = new BusSimulado();
    private final RevisarPlanillaServicio revisar = new RevisarPlanillaServicio(new ObrasEnMemoria(obra), planillas,
            sagas, bus, reloj);

    private static String codigo(Runnable accion) {
        try {
            accion.run();
        } catch (ReglaNegocioException e) {
            return e.codigo();
        }
        throw new AssertionError("Se esperaba ReglaNegocioException");
    }

    @Test
    void aprobarPasaAAprobandoCreaLaSagaYEnviaReservarSaldoPorElNeto() {
        Planilla planilla = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);

        assertThat(revisar.aprobar(planilla.id(), cliente)).isEqualTo(EstadoPlanilla.APROBANDO);

        assertThat(planillas.leer(planilla.id()).estado()).isEqualTo(EstadoPlanilla.APROBANDO);
        SagaAprobacion saga = sagas.unica();
        assertThat(saga.planillaId()).isEqualTo(planilla.id());
        assertThat(saga.estado()).isEqualTo(EstadoSaga.EN_CURSO);
        assertThat(saga.paso()).isEqualTo(PasoSaga.RESERVAR_SALDO);
        assertThat(saga.venceEn()).isEqualTo(reloj.ahora().plus(Duration.ofSeconds(60)));
        assertThat(bus.claves()).containsExactly("comando.finanzas.ReservarSaldo");
        ReservarSaldo reservar = bus.ultimo(ReservarSaldo.class);
        assertThat(reservar.correlacionId()).isEqualTo(saga.id());
        assertThat(reservar.planillaId()).isEqualTo(planilla.id());
        assertThat(reservar.obraId()).isEqualTo(obra.id());
        // 4 m2 × 100 = 400; − 10 % − 7 % = 332,00
        assertThat(reservar.monto()).isEqualByComparingTo("332.00");
        assertThat(reservar.monto().scale()).isEqualTo(2);
    }

    @Test
    void soloSeApruebaUObservaUnaPresentada() {
        for (EstadoPlanilla estado : new EstadoPlanilla[] {EstadoPlanilla.APROBANDO, EstadoPlanilla.APROBADA,
                EstadoPlanilla.OBSERVADA}) {
            Planilla planilla = planillas.agregar(obra, estado);
            assertThat(codigo(() -> revisar.aprobar(planilla.id(), cliente))).isEqualTo("PLANILLA_NO_PRESENTADA");
            assertThat(codigo(() -> revisar.observar(planilla.id(), "Faltan fotos de la losa", cliente)))
                    .isEqualTo("PLANILLA_NO_PRESENTADA");
        }
        assertThat(sagas.todas()).isEmpty();
        assertThat(bus.publicados()).isEmpty();
    }

    @Test
    void elBorradorNoExisteParaElClienteYOtroClienteNoLaVe() {
        Planilla borrador = planillas.agregar(obra, EstadoPlanilla.BORRADOR);
        Planilla presentada = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);

        assertThat(codigo(() -> revisar.aprobar(borrador.id(), cliente))).isEqualTo("PLANILLA_NO_ENCONTRADA");
        assertThat(codigo(() -> revisar.aprobar(presentada.id(), otroCliente))).isEqualTo("PLANILLA_NO_ENCONTRADA");
        // Ve la obra como gerente, pero no es su cliente
        assertThat(codigo(() -> revisar.aprobar(presentada.id(), gerenteYCliente))).isEqualTo("SIN_PERMISO");
        assertThat(codigo(() -> revisar.observar(presentada.id(), "Faltan fotos de la losa", gerenteYCliente)))
                .isEqualTo("SIN_PERMISO");
        assertThat(planillas.leer(presentada.id()).estado()).isEqualTo(EstadoPlanilla.PRESENTADA);
    }

    @Test
    void observarGuardaElMotivoYPublicaPlanillaObservadaDelCliente() {
        Planilla planilla = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);

        PlanillaDetalle detalle = revisar.observar(planilla.id(), "  Faltan fotos de la losa del 3er piso  ", cliente);

        assertThat(detalle.planilla().estado()).isEqualTo(EstadoPlanilla.OBSERVADA);
        assertThat(detalle.planilla().observacion()).isEqualTo("Faltan fotos de la losa del 3er piso");
        assertThat(planillas.leer(planilla.id()).observacion()).isEqualTo("Faltan fotos de la losa del 3er piso");
        assertThat(bus.claves()).containsExactly("evento.obras.PlanillaObservada");
        PlanillaObservada evento = bus.ultimo(PlanillaObservada.class);
        assertThat(evento.datos()).isEqualTo(new PlanillaObservada.Datos(planilla.id(), obra.id(),
                "Faltan fotos de la losa del 3er piso", "CLIENTE"));
        assertThat(evento.correlacionId()).isEqualTo(planilla.id());
        assertThat(sagas.todas()).isEmpty();
    }

    @Test
    void elMotivoTieneDe10A500Caracteres() {
        Planilla planilla = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);

        assertThat(codigo(() -> revisar.observar(planilla.id(), null, cliente))).isEqualTo("MOTIVO_INVALIDO");
        assertThat(codigo(() -> revisar.observar(planilla.id(), "123456789", cliente))).isEqualTo("MOTIVO_INVALIDO");
        assertThat(codigo(() -> revisar.observar(planilla.id(), "   123456789   ", cliente)))
                .isEqualTo("MOTIVO_INVALIDO");
        assertThat(codigo(() -> revisar.observar(planilla.id(), "x".repeat(501), cliente)))
                .isEqualTo("MOTIVO_INVALIDO");
        assertThat(planillas.leer(planilla.id()).estado()).isEqualTo(EstadoPlanilla.PRESENTADA);

        assertThat(revisar.observar(planilla.id(), "x".repeat(500), cliente).planilla().estado())
                .isEqualTo(EstadoPlanilla.OBSERVADA);
        Planilla otra = planillas.agregar(obra, EstadoPlanilla.PRESENTADA);
        assertThat(revisar.observar(otra.id(), "1234567890", cliente).planilla().observacion())
                .isEqualTo("1234567890");
    }
}
