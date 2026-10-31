package com.constructora.obras.saga.aplicacion;

import com.constructora.obras.compartido.dominio.ComandoDominio;
import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.Planilla.CantidadLinea;
import com.constructora.obras.saga.aplicacion.puerto.salida.SagaAprobacionRepositorio;
import com.constructora.obras.saga.dominio.EstadoSaga;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dobles a mano de los puertos para probar la revisión de la planilla y la saga sin Spring ni RabbitMQ: obras,
 * planillas y sagas en memoria, un reloj que se adelanta y un bus simulado que guarda lo que se publicó.
 */
public final class DoblesSaga {

    private DoblesSaga() {
    }

    /** Reloj que empieza el 29/10/2026 a las 12:00 en La Paz y se adelanta a mano. */
    public static final class RelojManual implements Reloj {

        private Instant ahora = Instant.parse("2026-10-29T16:00:00Z");

        @Override
        public Instant ahora() {
            return ahora;
        }

        public void adelantar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
    }

    /** Bus simulado: lo que se habría guardado en el outbox, en orden. */
    public static final class BusSimulado implements PublicadorEventos {

        private final List<EventoDominio> publicados = new ArrayList<>();

        @Override
        public void publicar(EventoDominio evento) {
            publicados.add(evento);
        }

        public List<EventoDominio> publicados() {
            return List.copyOf(publicados);
        }

        /** Claves de enrutamiento de lo publicado, como las armaría el outbox. */
        public List<String> claves() {
            return publicados.stream().map(DoblesSaga::clave).toList();
        }

        /** Lo publicado y lo olvida (para mirar solo lo que publica la acción siguiente). */
        public List<EventoDominio> tomar() {
            List<EventoDominio> tomados = List.copyOf(publicados);
            publicados.clear();
            return tomados;
        }

        @SuppressWarnings("unchecked")
        public <T extends EventoDominio> T ultimo(Class<T> tipo) {
            for (int i = publicados.size() - 1; i >= 0; i--) {
                if (tipo.isInstance(publicados.get(i))) {
                    return (T) publicados.get(i);
                }
            }
            throw new AssertionError("No se publicó " + tipo.getSimpleName());
        }
    }

    public static String clave(EventoDominio evento) {
        return evento instanceof ComandoDominio comando
                ? "comando." + comando.destino() + "." + comando.tipo()
                : "evento.obras." + evento.tipo();
    }

    /** Obra en ejecución con una partida P-01 Losa 10 m2 × 100 (contrato Bs 1.000, anticipo 10 %, retención 7 %). */
    public static Obra obra() {
        Asignaciones asignaciones = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        Partida losa = new Partida(UUID.randomUUID(), "P-01", "Losa", "m2", new BigDecimal("10"),
                new BigDecimal("100"), "oct", List.of());
        return Obra.reconstruir(UUID.randomUUID(), "Obra", "Santa Cruz", null, LocalDate.of(2026, 10, 1),
                EstadoObra.EN_EJECUCION, asignaciones,
                new ContratoCopia(UUID.randomUUID(), Dinero.de(new BigDecimal("1000")), 100, BigDecimal.TEN,
                        BigDecimal.valueOf(7)), null, List.of(losa));
    }

    public static final class ObrasEnMemoria implements ObraRepositorio {

        private final Map<UUID, Obra> obras = new HashMap<>();

        public ObrasEnMemoria(Obra... iniciales) {
            for (Obra obra : iniciales) {
                obras.put(obra.id(), obra);
            }
        }

        @Override
        public void guardar(Obra obra) {
            obras.put(obra.id(), obra);
        }

        @Override
        public Optional<Obra> buscarPorId(UUID id) {
            return Optional.ofNullable(obras.get(id));
        }

        @Override
        public List<Obra> listarTodas(FiltroObras filtro) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
                boolean comoAlmacenero, boolean comoCliente, FiltroObras filtro) {
            throw new UnsupportedOperationException();
        }
    }

    /** Guarda como la base: sube la versión en cada cambio y rechaza una versión vieja (VERSION_DESACTUALIZADA). */
    public static final class PlanillasEnMemoria implements PlanillaRepositorio {

        private final Map<UUID, Planilla> planillas = new LinkedHashMap<>();

        /** Planilla del periodo 2026-10 con 4 m2 de losa (subtotal 400, neto 332) en el estado indicado. */
        public Planilla agregar(Obra obra, EstadoPlanilla estado) {
            Planilla base = Planilla.crear(obra, Periodo.de("2026-10"), 1, false, Optional.empty(),
                    LocalDate.of(2026, 10, 29));
            base.actualizarLineas(List.of(new CantidadLinea(obra.partidas().getFirst().id(), new BigDecimal("4"))),
                    Map.of(), obra);
            Planilla planilla = Planilla.reconstruir(base.id(), base.obraId(), 1, base.periodo(), estado,
                    base.lineas(), base.totales(), null, null, null, 0, List.of());
            planillas.put(planilla.id(), planilla);
            return copia(planilla);
        }

        public Planilla leer(UUID id) {
            return copia(planillas.get(id));
        }

        @Override
        public Optional<Planilla> buscarPorId(UUID id) {
            return Optional.ofNullable(planillas.get(id)).map(PlanillasEnMemoria::copia);
        }

        @Override
        public List<Planilla> listarDeObra(UUID obraId, boolean incluirBorradores) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hayAbierta(UUID obraId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Periodo> periodoUltima(UUID obraId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int siguienteNumero(UUID obraId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, BigDecimal> acumuladosAnteriores(UUID obraId, Periodo periodo) {
            return Map.of();
        }

        @Override
        public Planilla guardar(Planilla planilla) {
            Planilla guardada = planillas.get(planilla.id());
            if (guardada != null && guardada.version() != planilla.version()) {
                throw new ReglaNegocioException(Planilla.VERSION_DESACTUALIZADA, "La planilla cambió");
            }
            int version = guardada == null ? 0 : planilla.version() + 1;
            Planilla nueva = Planilla.reconstruir(planilla.id(), planilla.obraId(), planilla.numero(),
                    planilla.periodo(), planilla.estado(), planilla.lineas(), planilla.totales(),
                    planilla.observacion(), planilla.cobroId(), planilla.cobroNumero(), version, planilla.fotoIds());
            planillas.put(nueva.id(), nueva);
            return copia(nueva);
        }

        private static Planilla copia(Planilla p) {
            return Planilla.reconstruir(p.id(), p.obraId(), p.numero(), p.periodo(), p.estado(), p.lineas(),
                    p.totales(), p.observacion(), p.cobroId(), p.cobroNumero(), p.version(), p.fotoIds());
        }
    }

    /** Sagas en memoria; guarda copias para que un cambio sin guardar no se vea. */
    public static final class SagasEnMemoria implements SagaAprobacionRepositorio {

        private final Map<UUID, SagaAprobacion> sagas = new LinkedHashMap<>();

        @Override
        public void guardar(SagaAprobacion saga) {
            sagas.put(saga.id(), copia(saga));
        }

        @Override
        public Optional<SagaAprobacion> buscarParaActualizar(UUID id) {
            return Optional.ofNullable(sagas.get(id)).map(SagasEnMemoria::copia);
        }

        @Override
        public List<UUID> vencidas(Instant ahora) {
            return sagas.values().stream()
                    .filter(saga -> saga.estado() == EstadoSaga.EN_CURSO && saga.venceEn().isBefore(ahora))
                    .map(SagaAprobacion::id)
                    .toList();
        }

        public List<SagaAprobacion> todas() {
            return sagas.values().stream().map(SagasEnMemoria::copia).toList();
        }

        public SagaAprobacion unica() {
            if (sagas.size() != 1) {
                throw new AssertionError("Se esperaba una saga y hay " + sagas.size());
            }
            return todas().getFirst();
        }

        private static SagaAprobacion copia(SagaAprobacion s) {
            return SagaAprobacion.reconstruir(s.id(), s.planillaId(), s.paso(), s.estado(), s.reservaId(),
                    s.cobroId(), s.cobroNumero(), s.motivo(), s.venceEn(), s.actualizadaEn());
        }
    }
}
