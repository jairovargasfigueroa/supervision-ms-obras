package com.constructora.obras.obra.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso.Resultado;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.obra.aplicacion.servicio.ConsultarMaterialPrevistoServicio;
import com.constructora.obras.obra.aplicacion.servicio.ConsultarObraServicio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.InsumoPrevisto;
import com.constructora.obras.obra.dominio.MaterialPrevisto;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.PlanillaCorta;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 006 · Consultar obra: casos de uso con un Reloj fijo y dobles a mano de los puertos. */
class ConsultarObraServicioTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final Usuario ADMIN = new Usuario("u-admin", Set.of("admin"));

    private final Asignaciones asignaciones = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID());
    private final Obra obra = obra("Edificio Las Palmas", EstadoObra.EN_EJECUCION, asignaciones);
    private final UUID losa = obra.partidas().getFirst().id();
    private final ObrasEnMemoria obras = new ObrasEnMemoria();
    private final PlanillasEnMemoria planillas = new PlanillasEnMemoria();

    /** Plazo de 300 días, una partida de 10 m2 × Bs 100 con cemento 1,1 por m2. */
    private static Obra obra(String nombre, EstadoObra estado, Asignaciones asignaciones) {
        Partida losa = Partida.nueva("P-01", "Losa", "m2", BigDecimal.TEN, BigDecimal.valueOf(100), null,
                List.of(new InsumoPrevisto("cemento", new BigDecimal("1.1"))));
        return Obra.reconstruir(UUID.randomUUID(), nombre, "Santa Cruz", INICIO, estado, asignaciones,
                ContratoCopia.nuevo(Dinero.de(new BigDecimal("1000")), 300, BigDecimal.TEN, BigDecimal.valueOf(7)),
                null, List.of(losa));
    }

    private ConsultarObraServicio servicio(String instante) {
        Reloj reloj = () -> Instant.parse(instante);
        return new ConsultarObraServicio(obras, planillas, reloj);
    }

    @Test
    void losIndicadoresUsanLaFechaDelRelojEnLaZonaDelNegocio() {
        obras.con(obra);
        planillas.ejecutadas.put(obra.id(), Map.of(losa, new BigDecimal("1.5")));

        // 2027-01-10 en La Paz: 70 días de 300 → 23,33 %; avance 1,5 × 100 / 1000 = 15 %
        ObraDetalle detalle = servicio("2027-01-10T16:00:00Z").obtener(obra.id(), ADMIN);
        assertThat(detalle.indicadores().plazoPct()).isEqualByComparingTo("23.33");
        assertThat(detalle.indicadores().avancePct()).isEqualByComparingTo("15");
        assertThat(detalle.indicadores().ejecutadaPorPartida().get(losa)).isEqualByComparingTo("1.5");
        assertThat(detalle.indicadores().enRiesgo()).isFalse();
        // 2026-11-02 a las 03:00 UTC todavía es 1 de noviembre en La Paz (UTC−4): día 0
        assertThat(servicio("2026-11-02T03:00:00Z").obtener(obra.id(), ADMIN).indicadores().plazoPct())
                .isEqualByComparingTo("0");
        assertThat(servicio("2026-11-02T04:00:00Z").obtener(obra.id(), ADMIN).indicadores().plazoPct())
                .isEqualByComparingTo("0.33");
    }

    @Test
    void laListaTraeIndicadoresYUltimaPlanillaDeCadaObra() {
        Obra sinPlanillas = obra("Casa Sur", EstadoObra.SIN_CONTRATO, asignaciones);
        obras.con(obra).con(sinPlanillas);
        planillas.ejecutadas.put(obra.id(), Map.of(losa, new BigDecimal("3")));
        planillas.ultimas.put(obra.id(), new PlanillaCorta(Periodo.de("2027-02"), EstadoPlanilla.PRESENTADA));

        List<ObraDetalle> lista = servicio("2027-03-11T16:00:00Z").listar(ADMIN, FiltroObras.NINGUNO);

        assertThat(lista).extracting(detalle -> detalle.obra().nombre()).containsExactly("Casa Sur",
                "Edificio Las Palmas");
        ObraDetalle conPlanillas = lista.get(1);
        // 130 días de 300 → 43,33 %; avance 30 %: 13,33 puntos, en riesgo
        assertThat(conPlanillas.indicadores().avancePct()).isEqualByComparingTo("30");
        assertThat(conPlanillas.indicadores().plazoPct()).isEqualByComparingTo("43.33");
        assertThat(conPlanillas.indicadores().enRiesgo()).isTrue();
        assertThat(conPlanillas.ultimaPlanilla())
                .isEqualTo(new PlanillaCorta(Periodo.de("2027-02"), EstadoPlanilla.PRESENTADA));
        assertThat(lista.get(0).ultimaPlanilla()).isNull();
        assertThat(lista.get(0).indicadores().avancePct()).isEqualByComparingTo("0");
    }

    @Test
    void elClienteSoloComoClienteNoVeUnBorradorComoUltimaPlanilla() {
        obras.con(obra);
        planillas.ultimas.put(obra.id(), new PlanillaCorta(Periodo.de("2027-03"), EstadoPlanilla.BORRADOR));
        planillas.ultimasSinBorrador.put(obra.id(),
                new PlanillaCorta(Periodo.de("2027-02"), EstadoPlanilla.APROBADA));
        String cliente = asignaciones.clienteId().toString();

        // 007: desde el portal (solo el rol cliente) la última planilla salta los BORRADOR
        assertThat(servicio("2027-03-11T16:00:00Z").listar(new Usuario(cliente, Set.of("cliente")),
                FiltroObras.NINGUNO).getFirst().ultimaPlanilla())
                .isEqualTo(new PlanillaCorta(Periodo.de("2027-02"), EstadoPlanilla.APROBADA));
        // Quien además la ve con un rol interno (el mismo usuario es su residente) ve el BORRADOR
        Obra propia = obra("Obra propia", EstadoObra.EN_EJECUCION, new Asignaciones(UUID.fromString(cliente),
                UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(cliente)));
        obras.con(propia);
        planillas.ultimas.put(propia.id(), new PlanillaCorta(Periodo.de("2027-03"), EstadoPlanilla.BORRADOR));
        assertThat(servicio("2027-03-11T16:00:00Z").obtener(propia.id(),
                new Usuario(cliente, Set.of("cliente", "residente"))).ultimaPlanilla().estado())
                .isEqualTo(EstadoPlanilla.BORRADOR);
        assertThat(servicio("2027-03-11T16:00:00Z").listar(ADMIN, FiltroObras.NINGUNO))
                .extracting(detalle -> detalle.ultimaPlanilla().estado())
                .containsOnly(EstadoPlanilla.BORRADOR);
    }

    @Test
    void elFiltroLlegaAlRepositorio() {
        obras.con(obra).con(obra("Casa Sur", EstadoObra.SIN_CONTRATO, asignaciones));

        assertThat(servicio("2027-01-10T16:00:00Z").listar(ADMIN, new FiltroObras(EstadoObra.SIN_CONTRATO, null)))
                .extracting(detalle -> detalle.obra().nombre()).containsExactly("Casa Sur");
        assertThat(servicio("2027-01-10T16:00:00Z").listar(ADMIN, new FiltroObras(null, "palmas")))
                .extracting(detalle -> detalle.obra().nombre()).containsExactly("Edificio Las Palmas");
    }

    @Test
    void cadaRolVeSoloLasObrasQueLeCorresponden() {
        Obra ajena = obra("Obra ajena", EstadoObra.EN_EJECUCION, new Asignaciones(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        obras.con(obra).con(ajena);
        ConsultarObraServicio servicio = servicio("2027-01-10T16:00:00Z");

        assertThat(servicio.listar(new Usuario("u-gerente", Set.of("gerente")), FiltroObras.NINGUNO)).hasSize(2);
        for (Usuario asignado : List.of(
                new Usuario(asignaciones.residenteId().toString(), Set.of("residente")),
                new Usuario(asignaciones.maestroId().toString(), Set.of("maestro")),
                new Usuario(asignaciones.almaceneroId().toString(), Set.of("almacenero")),
                new Usuario(asignaciones.clienteId().toString(), Set.of("cliente")))) {
            assertThat(servicio.listar(asignado, FiltroObras.NINGUNO)).extracting(detalle -> detalle.obra().id())
                    .containsExactly(obra.id());
            assertThatThrownBy(() -> servicio.obtener(ajena.id(), asignado))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_ENCONTRADA"));
        }
        assertThat(servicio.listar(new Usuario("no-es-uuid", Set.of("residente")), FiltroObras.NINGUNO)).isEmpty();
        assertThat(servicio.listar(new Usuario(asignaciones.residenteId().toString(), Set.of()), FiltroObras.NINGUNO))
                .isEmpty();
    }

    @Test
    void materialPrevistoConYSinPlanillaEnElPeriodo() {
        obras.con(obra);
        planillas.periodos.put(Periodo.de("2027-02"), Map.of(losa, new BigDecimal("4")));
        ConsultarMaterialPrevistoServicio servicio = new ConsultarMaterialPrevistoServicio(obras, planillas);

        Resultado con = servicio.consultar(obra.id(), Periodo.de("2027-02"), ADMIN);
        assertThat(con.hayPlanilla()).isTrue();
        assertThat(con.items()).containsExactly(new MaterialPrevisto(losa, "cemento", new BigDecimal("4.400")));

        Resultado sin = servicio.consultar(obra.id(), Periodo.de("2027-03"), ADMIN);
        assertThat(sin.periodo()).isEqualTo(Periodo.de("2027-03"));
        assertThat(sin.hayPlanilla()).isFalse();
        assertThat(sin.items()).isEmpty();
    }

    @Test
    void materialPrevistoSoloParaAdminGerenteYResidenteAsignado() {
        obras.con(obra);
        ConsultarMaterialPrevistoServicio servicio = new ConsultarMaterialPrevistoServicio(obras, planillas);
        Periodo periodo = Periodo.de("2027-02");

        assertThat(servicio.consultar(obra.id(), periodo,
                new Usuario(asignaciones.residenteId().toString(), Set.of("residente"))).hayPlanilla()).isFalse();
        assertThatThrownBy(() -> servicio.consultar(obra.id(), periodo,
                new Usuario(UUID.randomUUID().toString(), Set.of("residente"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_ENCONTRADA"));
        assertThatThrownBy(() -> servicio.consultar(obra.id(), periodo,
                new Usuario(asignaciones.maestroId().toString(), Set.of("residente", "maestro"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("SIN_PERMISO"));
    }

    /** Filtra y ordena en memoria como lo hace la base (estado exacto, nombre que contiene el texto). */
    private static final class ObrasEnMemoria implements ObraRepositorio {

        private final Map<UUID, Obra> obras = new LinkedHashMap<>();

        ObrasEnMemoria con(Obra obra) {
            obras.put(obra.id(), obra);
            return this;
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
            return obras.values().stream()
                    .filter(obra -> filtro.estado() == null || obra.estado() == filtro.estado())
                    .filter(obra -> filtro.busqueda() == null
                            || obra.nombre().toLowerCase().contains(filtro.busqueda().toLowerCase()))
                    .sorted(Comparator.comparing(Obra::nombre))
                    .toList();
        }

        @Override
        public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
                boolean comoAlmacenero, boolean comoCliente, FiltroObras filtro) {
            return listarTodas(filtro).stream()
                    .filter(obra -> comoResidente && obra.asignaciones().residenteId().equals(usuarioId)
                            || comoMaestro && obra.asignaciones().maestroId().equals(usuarioId)
                            || comoAlmacenero && obra.asignaciones().almaceneroId().equals(usuarioId)
                            || comoCliente && obra.asignaciones().clienteId().equals(usuarioId))
                    .toList();
        }
    }

    private static final class PlanillasEnMemoria implements PlanillasObraRepositorio {

        private final Map<UUID, Map<UUID, BigDecimal>> ejecutadas = new HashMap<>();
        private final Map<UUID, PlanillaCorta> ultimas = new HashMap<>();
        private final Map<UUID, PlanillaCorta> ultimasSinBorrador = new HashMap<>();
        private final Map<Periodo, Map<UUID, BigDecimal>> periodos = new HashMap<>();

        @Override
        public Map<UUID, Map<UUID, BigDecimal>> ejecutadaAcumulada(Collection<UUID> obraIds) {
            return ejecutadas;
        }

        @Override
        public Map<UUID, PlanillaCorta> ultimas(Collection<UUID> obraIds) {
            return de(ultimas, obraIds);
        }

        @Override
        public Map<UUID, PlanillaCorta> ultimasSinBorrador(Collection<UUID> obraIds) {
            return de(ultimasSinBorrador, obraIds);
        }

        private static Map<UUID, PlanillaCorta> de(Map<UUID, PlanillaCorta> todas, Collection<UUID> obraIds) {
            Map<UUID, PlanillaCorta> pedidas = new HashMap<>(todas);
            pedidas.keySet().retainAll(obraIds);
            return pedidas;
        }

        @Override
        public Optional<Map<UUID, BigDecimal>> cantidadesDelPeriodo(UUID obraId, Periodo periodo) {
            return Optional.ofNullable(periodos.get(periodo));
        }
    }
}
