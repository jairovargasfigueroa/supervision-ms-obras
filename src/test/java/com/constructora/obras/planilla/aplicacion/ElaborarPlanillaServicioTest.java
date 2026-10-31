package com.constructora.obras.planilla.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ElaborarPlanillaCasoUso.Guardado;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.aplicacion.servicio.ConsultarPlanillaServicio;
import com.constructora.obras.planilla.aplicacion.servicio.ElaborarPlanillaServicio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.Planilla.CantidadLinea;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 007 · Casos de uso de planillas con un Reloj fijo (29/10/2026 en La Paz) y dobles a mano de los puertos. */
class ElaborarPlanillaServicioTest {

    private static final Reloj RELOJ = () -> Instant.parse("2026-10-29T16:00:00Z");

    private final Asignaciones asignaciones = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID());
    private final Partida losa = new Partida(UUID.randomUUID(), "P-01", "Losa", "m2", new BigDecimal("10"),
            new BigDecimal("100"), "oct", List.of());
    private final Obra obra = Obra.reconstruir(UUID.randomUUID(), "Obra", "Santa Cruz", null, LocalDate.of(2026, 10, 1),
            EstadoObra.EN_EJECUCION, asignaciones,
            new ContratoCopia(UUID.randomUUID(), Dinero.de(new BigDecimal("1000")), 100, BigDecimal.TEN,
                    BigDecimal.valueOf(7)), null, List.of(losa));
    private final Usuario residente = new Usuario(asignaciones.residenteId().toString(), Set.of("residente"));
    private final Usuario cliente = new Usuario(asignaciones.clienteId().toString(), Set.of("cliente"));
    private final Usuario gerente = new Usuario("u-gerente", Set.of("gerente"));

    private final ObrasEnMemoria obras = new ObrasEnMemoria(obra);
    private final PlanillasEnMemoria planillas = new PlanillasEnMemoria();
    private final FotosEnMemoria fotos = new FotosEnMemoria();
    private final ElaborarPlanillaServicio elaborar = new ElaborarPlanillaServicio(obras, planillas, fotos, RELOJ);
    private final ConsultarPlanillaServicio consultar = new ConsultarPlanillaServicio(obras, planillas);

    private static String codigo(Runnable accion) {
        try {
            accion.run();
        } catch (ReglaNegocioException e) {
            return e.codigo();
        }
        throw new AssertionError("Se esperaba ReglaNegocioException");
    }

    @Test
    void elResidenteAsignadoCreaGuardaYPresentaYLasFotosSePublican() {
        PlanillaDetalle creada = elaborar.crear(obra.id(), Periodo.de("2026-10"), residente);
        UUID id = creada.planilla().id();
        FotoAvance foto = fotos.agregar(obra.id(), "2026-10-20T15:00:00Z");

        PlanillaDetalle guardada = elaborar.guardar(new Guardado(id, 0,
                List.of(new CantidadLinea(losa.id(), new BigDecimal("2.5"))), List.of(foto.id())), residente);
        PlanillaDetalle presentada = elaborar.presentar(id, 1, residente);

        assertThat(creada.planilla().numero()).isEqualTo(1);
        assertThat(creada.anticipoPct()).isEqualByComparingTo("10");
        assertThat(guardada.planilla().totales().neto()).isEqualTo(Dinero.de(new BigDecimal("207.50")));
        assertThat(guardada.lineas().getFirst().saldo()).isEqualByComparingTo("7.5");
        assertThat(presentada.planilla().estado()).isEqualTo(EstadoPlanilla.PRESENTADA);
        assertThat(fotos.fotos.get(foto.id()).publicadaCliente()).isTrue();
    }

    @Test
    void elNumeroEsElSiguienteYElAcumuladoAnteriorVieneDeLasAprobadas() {
        planillas.agregar(obra, 1, "2026-09", EstadoPlanilla.APROBADA, new BigDecimal("4"));

        PlanillaDetalle creada = elaborar.crear(obra.id(), Periodo.de("2026-10"), residente);

        assertThat(creada.planilla().numero()).isEqualTo(2);
        assertThat(creada.lineas().getFirst().acumuladoAnterior()).isEqualByComparingTo("4");
        assertThat(codigo(() -> elaborar.guardar(new Guardado(creada.planilla().id(), 0,
                List.of(new CantidadLinea(losa.id(), new BigDecimal("6.001"))), List.of()), residente)))
                .isEqualTo("SUPERA_CANTIDAD_CONTRATADA");
    }

    @Test
    void soloElResidenteAsignadoElabora() {
        UUID id = elaborar.crear(obra.id(), Periodo.de("2026-10"), residente).planilla().id();
        Usuario otroResidente = new Usuario(UUID.randomUUID().toString(), Set.of("residente"));
        Usuario maestroYResidente = new Usuario(asignaciones.maestroId().toString(), Set.of("residente", "maestro"));

        assertThat(codigo(() -> elaborar.crear(obra.id(), Periodo.de("2026-10"), otroResidente)))
                .isEqualTo("OBRA_NO_ENCONTRADA");
        assertThat(codigo(() -> elaborar.presentar(id, 0, otroResidente))).isEqualTo("PLANILLA_NO_ENCONTRADA");
        assertThat(codigo(() -> elaborar.crear(obra.id(), Periodo.de("2026-10"), maestroYResidente)))
                .isEqualTo("SIN_PERMISO");
        assertThat(codigo(() -> elaborar.guardar(new Guardado(id, 0, List.of(), List.of()), gerente)))
                .isEqualTo("SIN_PERMISO");
        assertThat(codigo(() -> elaborar.presentar(UUID.randomUUID(), 0, residente)))
                .isEqualTo("PLANILLA_NO_ENCONTRADA");
    }

    @Test
    void elClienteNoVeLosBorradoresYAdminYGerenteLeen() {
        Planilla aprobada = planillas.agregar(obra, 1, "2026-09", EstadoPlanilla.APROBADA, BigDecimal.ONE);
        UUID borrador = elaborar.crear(obra.id(), Periodo.de("2026-10"), residente).planilla().id();

        assertThat(consultar.listar(obra.id(), cliente)).extracting(Planilla::id).containsExactly(aprobada.id());
        assertThat(consultar.listar(obra.id(), gerente)).extracting(Planilla::numero).containsExactly(1, 2);
        assertThat(codigo(() -> consultar.obtener(borrador, cliente))).isEqualTo("PLANILLA_NO_ENCONTRADA");
        assertThat(consultar.obtener(aprobada.id(), cliente).planilla().estado()).isEqualTo(EstadoPlanilla.APROBADA);
        assertThat(consultar.obtener(borrador, new Usuario("u-admin", Set.of("admin"))).planilla().estado())
                .isEqualTo(EstadoPlanilla.BORRADOR);
        assertThat(codigo(() -> consultar.listar(obra.id(),
                new Usuario(UUID.randomUUID().toString(), Set.of("cliente"))))).isEqualTo("OBRA_NO_ENCONTRADA");
    }

    private static final class ObrasEnMemoria implements ObraRepositorio {

        private final Map<UUID, Obra> obras = new HashMap<>();

        ObrasEnMemoria(Obra obra) {
            obras.put(obra.id(), obra);
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

    /** Guarda como la base: sube la versión en cada cambio de una planilla existente. */
    private static final class PlanillasEnMemoria implements PlanillaRepositorio {

        private final Map<UUID, Planilla> planillas = new LinkedHashMap<>();

        Planilla agregar(Obra obra, int numero, String periodo, EstadoPlanilla estado, BigDecimal cantidad) {
            Planilla base = Planilla.crear(obra, Periodo.de(periodo), numero, false, Optional.empty(),
                    LocalDate.of(2026, 10, 29));
            base.actualizarLineas(List.of(new CantidadLinea(obra.partidas().getFirst().id(), cantidad)), Map.of(),
                    obra);
            Planilla planilla = Planilla.reconstruir(base.id(), base.obraId(), numero, base.periodo(), estado,
                    base.lineas(), base.totales(), null, null, null, 0, List.of());
            planillas.put(planilla.id(), planilla);
            return planilla;
        }

        @Override
        public Optional<Planilla> buscarPorId(UUID id) {
            return Optional.ofNullable(planillas.get(id)).map(PlanillasEnMemoria::copia);
        }

        @Override
        public List<Planilla> listarDeObra(UUID obraId, boolean incluirBorradores) {
            return planillas.values().stream()
                    .filter(planilla -> planilla.obraId().equals(obraId))
                    .filter(planilla -> incluirBorradores || planilla.estado() != EstadoPlanilla.BORRADOR)
                    .sorted(Comparator.comparing(Planilla::numero))
                    .toList();
        }

        @Override
        public boolean hayAbierta(UUID obraId) {
            return planillas.values().stream().anyMatch(p -> p.obraId().equals(obraId) && p.estaAbierta());
        }

        @Override
        public Optional<Periodo> periodoUltima(UUID obraId) {
            return planillas.values().stream().filter(p -> p.obraId().equals(obraId)).map(Planilla::periodo)
                    .max(Comparator.comparing(Periodo::mes));
        }

        @Override
        public int siguienteNumero(UUID obraId) {
            return (int) planillas.values().stream().filter(p -> p.obraId().equals(obraId)).count() + 1;
        }

        @Override
        public Map<UUID, BigDecimal> acumuladosAnteriores(UUID obraId, Periodo periodo) {
            Map<UUID, BigDecimal> acumulados = new HashMap<>();
            planillas.values().stream()
                    .filter(p -> p.obraId().equals(obraId) && p.estado() == EstadoPlanilla.APROBADA
                            && p.periodo().mes().isBefore(periodo.mes()))
                    .flatMap(p -> p.lineas().stream())
                    .forEach(linea -> acumulados.merge(linea.partidaId(), linea.cantidadPeriodo(), BigDecimal::add));
            return acumulados;
        }

        @Override
        public Planilla guardar(Planilla planilla) {
            int version = planillas.containsKey(planilla.id()) ? planilla.version() + 1 : 0;
            Planilla guardada = Planilla.reconstruir(planilla.id(), planilla.obraId(), planilla.numero(),
                    planilla.periodo(), planilla.estado(), planilla.lineas(), planilla.totales(),
                    planilla.observacion(), planilla.cobroId(), planilla.cobroNumero(), version, planilla.fotoIds());
            planillas.put(guardada.id(), guardada);
            return copia(guardada);
        }

        private static Planilla copia(Planilla p) {
            return Planilla.reconstruir(p.id(), p.obraId(), p.numero(), p.periodo(), p.estado(), p.lineas(),
                    p.totales(), p.observacion(), p.cobroId(), p.cobroNumero(), p.version(), p.fotoIds());
        }
    }

    private static final class FotosEnMemoria implements FotoAvanceRepositorio {

        private final Map<UUID, FotoAvance> fotos = new HashMap<>();

        FotoAvance agregar(UUID obraId, String registradoEn) {
            UUID id = UUID.randomUUID();
            FotoAvance foto = new FotoAvance(id, obraId, UUID.randomUUID(), FotoAvance.claveArchivo(obraId, id),
                    false, UUID.randomUUID(), Instant.parse(registradoEn));
            fotos.put(id, foto);
            return foto;
        }

        @Override
        public void guardar(FotoAvance foto) {
            fotos.put(foto.id(), foto);
        }

        @Override
        public Optional<FotoAvance> buscarPorId(UUID id) {
            return Optional.ofNullable(fotos.get(id));
        }

        @Override
        public List<FotoAvance> buscarPorIds(Collection<UUID> ids) {
            return ids.stream().map(fotos::get).filter(Objects::nonNull).toList();
        }

        @Override
        public void publicar(Collection<UUID> ids) {
            ids.forEach(id -> fotos.computeIfPresent(id, (clave, f) -> new FotoAvance(f.id(), f.obraId(),
                    f.partidaId(), f.claveArchivo(), true, f.registradoPor(), f.registradoEn())));
        }

        @Override
        public Pagina<FotoAvance> listar(UUID obraId, UUID partidaId, boolean soloPublicadas, Paginacion paginacion) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void unaFotoDeOtroPeriodoNoSeAdjunta() {
        UUID id = elaborar.crear(obra.id(), Periodo.de("2026-10"), residente).planilla().id();
        FotoAvance setiembre = fotos.agregar(obra.id(), "2026-09-30T12:00:00Z");

        assertThatThrownBy(() -> elaborar.guardar(new Guardado(id, 0, List.of(), List.of(setiembre.id())),
                residente)).isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.codigo()).isEqualTo("FOTO_FUERA_DEL_PERIODO"));
    }
}
