package com.constructora.obras.planilla.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.planilla.dominio.Planilla.CantidadLinea;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Reglas de la planilla (docs/reglas-negocio.md, «Planilla de avance»; 007): caso fijo con contrato de Bs 100.000,00,
 * anticipo 10 % y retención 7 %, partidas P-01 200 m3 × 150,00 y P-02 100 m2 × 700,00.
 */
class PlanillaTest {

    static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    static final Partida P01 = new Partida(UUID.randomUUID(), "P-01", "Excavación", "m3", new BigDecimal("200"),
            new BigDecimal("150.00"), "nov", List.of());
    static final Partida P02 = new Partida(UUID.randomUUID(), "P-02", "Muro de ladrillo", "m2", new BigDecimal("100"),
            new BigDecimal("700.00"), "nov-dic", List.of());

    static Obra obra(EstadoObra estado, ContratoCopia contrato, Partida... partidas) {
        return Obra.reconstruir(UUID.randomUUID(), "Obra de prueba", "Santa Cruz", null, LocalDate.of(2026, 11, 1),
                estado,
                new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                contrato, null, List.of(partidas));
    }

    static ContratoCopia contrato(String monto, String anticipo, String retencion) {
        return new ContratoCopia(UUID.randomUUID(), Dinero.de(new BigDecimal(monto)), 300, new BigDecimal(anticipo),
                new BigDecimal(retencion));
    }

    /** Obra en ejecución con P-02 cargada antes que P-01 (las líneas salen por código). */
    static Obra obraFija() {
        return obra(EstadoObra.EN_EJECUCION, contrato("100000", "10", "7"), P02, P01);
    }

    static Planilla nueva(Obra obra) {
        return Planilla.crear(obra, Periodo.de("2026-10"), 1, false, Optional.empty(), HOY);
    }

    static Planilla enEstado(Planilla base, EstadoPlanilla estado, int version) {
        return Planilla.reconstruir(base.id(), base.obraId(), base.numero(), base.periodo(), estado, base.lineas(),
                base.totales(), "Falta la foto del muro", null, null, version, base.fotoIds());
    }

    static List<CantidadLinea> cantidades(Object... partidaYCantidad) {
        List<CantidadLinea> lista = new java.util.ArrayList<>();
        for (int i = 0; i < partidaYCantidad.length; i += 2) {
            lista.add(new CantidadLinea(((Partida) partidaYCantidad[i]).id(),
                    new BigDecimal((String) partidaYCantidad[i + 1])));
        }
        return lista;
    }

    static Dinero bs(String valor) {
        return Dinero.de(new BigDecimal(valor));
    }

    static ReglaNegocioException error(Runnable accion) {
        try {
            accion.run();
        } catch (ReglaNegocioException e) {
            return e;
        }
        throw new AssertionError("Se esperaba ReglaNegocioException");
    }

    @Nested
    class Crear {

        @Test
        void quedaEnBorradorConUnaLineaPorPartidaEnCero() {
            Obra obra = obraFija();

            Planilla planilla = Planilla.crear(obra, Periodo.de("2026-10"), 3, false,
                    Optional.of(Periodo.de("2026-09")), HOY);

            assertThat(planilla.estado()).isEqualTo(EstadoPlanilla.BORRADOR);
            assertThat(planilla.numero()).isEqualTo(3);
            assertThat(planilla.obraId()).isEqualTo(obra.id());
            assertThat(planilla.periodo()).isEqualTo(Periodo.de("2026-10"));
            assertThat(planilla.version()).isZero();
            assertThat(planilla.lineas()).extracting(LineaPlanilla::partidaId).containsExactly(P01.id(), P02.id());
            assertThat(planilla.lineas()).allSatisfy(linea -> {
                assertThat(linea.cantidadPeriodo()).isEqualByComparingTo("0");
                assertThat(linea.monto()).isEqualTo(Dinero.CERO);
            });
            assertThat(planilla.totales()).isEqualTo(TotalesPlanilla.CERO);
            assertThat(planilla.fotoIds()).isEmpty();
            assertThat(planilla.observacion()).isNull();
        }

        @Test
        void laObraDebeEstarEnEjecucion() {
            Obra sinContrato = obra(EstadoObra.SIN_CONTRATO, contrato("100000", "10", "7"), P01, P02);

            assertThat(error(() -> nueva(sinContrato)).codigo()).isEqualTo("OBRA_NO_EN_EJECUCION");
        }

        @Test
        void noSiHayOtraPlanillaSinAprobar() {
            ReglaNegocioException e = error(() -> Planilla.crear(obraFija(), Periodo.de("2026-10"), 2, true,
                    Optional.of(Periodo.de("2026-09")), HOY));

            assertThat(e.codigo()).isEqualTo("PLANILLA_ABIERTA_EXISTE");
            assertThat(e.getMessage())
                    .isEqualTo("Ya hay una planilla sin aprobar en esta obra. Termínala antes de crear otra.");
        }

        @Test
        void elPeriodoDebeSerPosteriorAlDeLaUltima() {
            for (String periodo : List.of("2026-09", "2026-08")) {
                ReglaNegocioException e = error(() -> Planilla.crear(obraFija(), Periodo.de(periodo), 2, false,
                        Optional.of(Periodo.de("2026-09")), HOY));

                assertThat(e.codigo()).isEqualTo("PERIODO_NO_POSTERIOR");
                assertThat(e.getMessage())
                        .isEqualTo("El periodo debe ser posterior al de la última planilla (2026-09).");
                assertThat(e.errores()).extracting(ErrorCampo::campo).containsExactly("periodo");
            }
        }

        @Test
        void noPuedeSerUnMesFuturo() {
            ReglaNegocioException e = error(() -> Planilla.crear(obraFija(), Periodo.de("2026-11"), 1, false,
                    Optional.empty(), HOY));

            assertThat(e.codigo()).isEqualTo("PERIODO_FUTURO");
            assertThat(e.getMessage()).isEqualTo("No se puede crear la planilla de un mes que todavía no llegó.");
        }

        @Test
        void elMesActualYUnoAnteriorSePermiten() {
            assertThat(Planilla.crear(obraFija(), Periodo.de("2026-10"), 1, false, Optional.empty(),
                    LocalDate.of(2026, 10, 1)).periodo()).isEqualTo(Periodo.de("2026-10"));
            assertThat(Planilla.crear(obraFija(), Periodo.de("2026-09"), 1, false, Optional.empty(), HOY).periodo())
                    .isEqualTo(Periodo.de("2026-09"));
        }

        @Test
        void laPlanillaAbiertaSeRevisaAntesQueElPeriodo() {
            assertThat(error(() -> Planilla.crear(obraFija(), Periodo.de("2027-01"), 2, true,
                    Optional.of(Periodo.de("2027-02")), HOY)).codigo()).isEqualTo("PLANILLA_ABIERTA_EXISTE");
            assertThat(error(() -> Planilla.crear(obraFija(), Periodo.de("2027-01"), 2, false,
                    Optional.of(Periodo.de("2027-02")), HOY)).codigo()).isEqualTo("PERIODO_NO_POSTERIOR");
        }
    }

    @Nested
    class Calculo {

        @Test
        void casoFijoConAnticipoDiezYRetencionSiete() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);

            // P-01: 120,5 × 150,00 = 18.075,00 · P-02: 33,333 × 700,00 = 23.333,10
            planilla.actualizarLineas(cantidades(P01, "120.5", P02, "33.333"), Map.of(), obra);

            assertThat(planilla.lineas()).extracting(LineaPlanilla::monto)
                    .containsExactly(bs("18075.00"), bs("23333.10"));
            // subtotal 41.408,10 · anticipo 10 % = 4.140,81 · retención 7 % = 2.898,567 → 2.898,57
            assertThat(planilla.totales()).isEqualTo(new TotalesPlanilla(bs("41408.10"), bs("4140.81"),
                    bs("2898.57"), bs("34368.72")));
        }

        @Test
        void ejemploDelContrato() {
            // 120 × 420,50 = 50.460,00 → 5.046,00 · 3.532,20 · 41.881,80 (docs/contratos.md, GET /planillas/{id})
            assertThat(TotalesPlanilla.calcular(List.of(bs("50460.00")), BigDecimal.TEN, BigDecimal.valueOf(7)))
                    .isEqualTo(new TotalesPlanilla(bs("50460.00"), bs("5046.00"), bs("3532.20"), bs("41881.80")));
        }

        @Test
        void redondeaMitadHaciaArribaAlFinalDeCadaMonto() {
            Partida barata = new Partida(UUID.randomUUID(), "P-01", "Limpieza", "m2", new BigDecimal("10"),
                    new BigDecimal("2.50"), "nov", List.of());
            Partida otra = new Partida(UUID.randomUUID(), "P-02", "Pintura", "m2", new BigDecimal("10"),
                    new BigDecimal("2.50"), "nov", List.of());
            Obra obra = obra(EstadoObra.EN_EJECUCION, contrato("50", "10", "7"), barata, otra);
            Planilla planilla = nueva(obra);

            // 0,1 × 2,50 = 0,25 · 0,003 × 2,50 = 0,0075 → 0,01
            planilla.actualizarLineas(cantidades(barata, "0.1", otra, "0.003"), Map.of(), obra);

            assertThat(planilla.lineas()).extracting(LineaPlanilla::monto).containsExactly(bs("0.25"), bs("0.01"));
            // subtotal 0,26 · anticipo 0,026 → 0,03 · retención 0,0182 → 0,02 · neto 0,21
            assertThat(planilla.totales()).isEqualTo(new TotalesPlanilla(bs("0.26"), bs("0.03"), bs("0.02"),
                    bs("0.21")));
            // 0,25 × 10 % = 0,025: mitad hacia arriba da 0,03 (mitad al par daría 0,02)
            assertThat(TotalesPlanilla.calcular(List.of(bs("0.25")), BigDecimal.TEN, BigDecimal.ZERO)
                    .descuentoAnticipo()).isEqualTo(bs("0.03"));
        }

        @Test
        void lasPartidasQueNoVienenQuedanComoEstaban() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            planilla.actualizarLineas(cantidades(P01, "10", P02, "1"), Map.of(), obra);

            planilla.actualizarLineas(cantidades(P02, "2"), Map.of(), obra);

            assertThat(planilla.lineas()).extracting(LineaPlanilla::cantidadPeriodo)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("10"), new BigDecimal("2"));
            assertThat(planilla.totales().subtotal()).isEqualTo(bs("2900.00"));
        }

        @Test
        void unaPartidaQueNoEsDeLaPlanillaORepetidaNoSeAcepta() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            Partida ajena = new Partida(UUID.randomUUID(), "P-09", "Otra", "m2", BigDecimal.ONE, BigDecimal.ONE, "nov",
                    List.of());

            ReglaNegocioException ajenaError = error(() -> planilla.actualizarLineas(
                    cantidades(P01, "1", ajena, "1"), Map.of(), obra));
            ReglaNegocioException repetida = error(() -> planilla.actualizarLineas(
                    cantidades(P01, "1", P01, "2"), Map.of(), obra));

            assertThat(ajenaError.codigo()).isEqualTo("DATOS_INVALIDOS");
            assertThat(ajenaError.errores()).extracting(ErrorCampo::campo).containsExactly("lineas[1].partidaId");
            assertThat(repetida.errores()).extracting(ErrorCampo::campo).containsExactly("lineas[1].partidaId");
            assertThat(error(() -> planilla.actualizarLineas(cantidades(P01, "-1"), Map.of(), obra)).errores())
                    .extracting(ErrorCampo::campo).containsExactly("lineas[0].cantidadPeriodo");
        }
    }

    @Nested
    class LimiteContratado {

        @Test
        void llegarJustoALoContratadoSePermite() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);

            planilla.actualizarLineas(cantidades(P01, "120", P02, "100"), Map.of(P01.id(), new BigDecimal("80")),
                    obra);

            assertThat(planilla.totales().subtotal()).isEqualTo(bs("88000.00"));
        }

        @Test
        void superarloPorUnMilesimoSeRechazaPorPartidaYNoCambiaNada() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            planilla.actualizarLineas(cantidades(P01, "5"), Map.of(), obra);

            ReglaNegocioException e = error(() -> planilla.actualizarLineas(
                    cantidades(P01, "120.001", P02, "100.5"), Map.of(P01.id(), new BigDecimal("80")), obra));

            assertThat(e.codigo()).isEqualTo("SUPERA_CANTIDAD_CONTRATADA");
            assertThat(e.getMessage()).isEqualTo("Hay filas que superan lo contratado. Corrígelas para guardar.");
            assertThat(e.errores()).containsExactly(
                    new ErrorCampo(P01.id().toString(), "Supera lo contratado (saldo: 120)"),
                    new ErrorCampo(P02.id().toString(), "Supera lo contratado (saldo: 100)"));
            assertThat(planilla.lineas().getFirst().cantidadPeriodo()).isEqualByComparingTo("5");
            assertThat(planilla.totales().subtotal()).isEqualTo(bs("750.00"));
        }

        @Test
        void elDetalleMuestraAcumuladosYSaldo() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            planilla.actualizarLineas(cantidades(P01, "20"), Map.of(P01.id(), new BigDecimal("80")), obra);

            List<Planilla.LineaDetalle> detalle = planilla.detalle(obra, Map.of(P01.id(), new BigDecimal("80")));

            assertThat(detalle).extracting(linea -> linea.partida().codigo()).containsExactly("P-01", "P-02");
            Planilla.LineaDetalle p01 = detalle.getFirst();
            assertThat(p01.acumuladoAnterior()).isEqualByComparingTo("80");
            assertThat(p01.cantidadPeriodo()).isEqualByComparingTo("20");
            assertThat(p01.acumuladoTotal()).isEqualByComparingTo("100");
            assertThat(p01.saldo()).isEqualByComparingTo("100");
            assertThat(p01.monto()).isEqualTo(bs("3000.00"));
            assertThat(detalle.get(1).saldo()).isEqualByComparingTo("100");
        }
    }

    @Nested
    class Estados {

        @Test
        void presentarUnBorradorConSubtotal() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            planilla.actualizarLineas(cantidades(P01, "1"), Map.of(), obra);

            planilla.presentar(0);

            assertThat(planilla.estado()).isEqualTo(EstadoPlanilla.PRESENTADA);
        }

        @Test
        void unaObservadaSeCorrigeYSeVuelveAPresentar() {
            Obra obra = obraFija();
            Planilla base = nueva(obra);
            base.actualizarLineas(cantidades(P01, "1"), Map.of(), obra);
            Planilla observada = enEstado(base, EstadoPlanilla.OBSERVADA, 4);

            observada.actualizarLineas(cantidades(P01, "2"), Map.of(), obra);
            observada.presentar(4);

            assertThat(observada.estado()).isEqualTo(EstadoPlanilla.PRESENTADA);
            assertThat(observada.observacion()).isEqualTo("Falta la foto del muro");
            assertThat(observada.totales().subtotal()).isEqualTo(bs("300.00"));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoPlanilla.class, names = {"PRESENTADA", "APROBANDO", "APROBADA"})
        void yaNoSeEditaNiSePresenta(EstadoPlanilla estado) {
            Obra obra = obraFija();
            Planilla base = nueva(obra);
            base.actualizarLineas(cantidades(P01, "1"), Map.of(), obra);
            Planilla planilla = enEstado(base, estado, 2);

            assertThat(planilla.esEditable()).isFalse();
            for (Runnable accion : List.<Runnable>of(
                    () -> planilla.verificarEdicion(2),
                    () -> planilla.actualizarLineas(cantidades(P01, "2"), Map.of(), obra),
                    () -> planilla.adjuntarFotos(List.of(), Map.of()),
                    () -> planilla.presentar(2))) {
                ReglaNegocioException e = error(accion);
                assertThat(e.codigo()).isEqualTo("PLANILLA_NO_EDITABLE");
                assertThat(e.getMessage()).isEqualTo("La planilla ya no se puede editar (estado " + estado + ")");
            }
            assertThat(planilla.estado()).isEqualTo(estado);
        }

        @Test
        void conOtraVersionSeAvisaQueCambio() {
            Obra obra = obraFija();
            Planilla planilla = enEstado(nueva(obra), EstadoPlanilla.BORRADOR, 3);

            ReglaNegocioException e = error(() -> planilla.verificarEdicion(2));

            assertThat(e.codigo()).isEqualTo("VERSION_DESACTUALIZADA");
            assertThat(e.getMessage()).isEqualTo("La planilla cambió, recarga para ver la última versión");
            assertThat(error(() -> planilla.presentar(4)).codigo()).isEqualTo("VERSION_DESACTUALIZADA");
        }

        @Test
        void conSubtotalCeroNoSePresenta() {
            Planilla planilla = nueva(obraFija());

            ReglaNegocioException e = error(() -> planilla.presentar(0));

            assertThat(e.codigo()).isEqualTo("SUBTOTAL_CERO");
            assertThat(planilla.estado()).isEqualTo(EstadoPlanilla.BORRADOR);
        }

        @Test
        void soloLaAprobadaDejaDeEstarAbierta() {
            Planilla planilla = nueva(obraFija());

            for (EstadoPlanilla estado : EstadoPlanilla.values()) {
                assertThat(enEstado(planilla, estado, 0).estaAbierta()).isEqualTo(estado != EstadoPlanilla.APROBADA);
            }
        }
    }

    @Nested
    class Fotos {

        static FotoAvance foto(UUID obraId, String registradoEn) {
            UUID id = UUID.randomUUID();
            return new FotoAvance(id, obraId, P01.id(), FotoAvance.claveArchivo(obraId, id), false, UUID.randomUUID(),
                    Instant.parse(registradoEn));
        }

        @Test
        void seAdjuntanFotosDeLaObraDelPeriodoEnLaZonaDelNegocio() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            // 1/10 00:00 y 31/10 23:30 en La Paz (UTC−4)
            FotoAvance primera = foto(obra.id(), "2026-10-01T04:00:00Z");
            FotoAvance ultima = foto(obra.id(), "2026-11-01T03:30:00Z");

            planilla.adjuntarFotos(List.of(primera.id(), ultima.id(), primera.id()),
                    Map.of(primera.id(), primera, ultima.id(), ultima));

            assertThat(planilla.fotoIds()).containsExactlyInAnyOrder(primera.id(), ultima.id());
            planilla.adjuntarFotos(List.of(), Map.of());
            assertThat(planilla.fotoIds()).isEmpty();
        }

        @Test
        void unaFotoDeOtroMesDeOtraObraOQueNoExisteSeRechaza() {
            Obra obra = obraFija();
            Planilla planilla = nueva(obra);
            FotoAvance buena = foto(obra.id(), "2026-10-15T12:00:00Z");
            planilla.adjuntarFotos(List.of(buena.id()), Map.of(buena.id(), buena));
            FotoAvance setiembre = foto(obra.id(), "2026-10-01T03:59:59Z");
            FotoAvance noviembre = foto(obra.id(), "2026-11-01T04:00:00Z");
            FotoAvance ajena = foto(UUID.randomUUID(), "2026-10-15T12:00:00Z");
            UUID inexistente = UUID.randomUUID();

            ReglaNegocioException e = error(() -> planilla.adjuntarFotos(
                    List.of(buena.id(), setiembre.id(), noviembre.id(), ajena.id(), inexistente),
                    Map.of(buena.id(), buena, setiembre.id(), setiembre, noviembre.id(), noviembre, ajena.id(),
                            ajena)));

            assertThat(e.codigo()).isEqualTo("FOTO_FUERA_DEL_PERIODO");
            assertThat(e.getMessage()).isEqualTo("Una de las fotos adjuntas no es de este periodo. Quítala para guardar.");
            assertThat(e.errores()).extracting(ErrorCampo::campo).containsExactly(setiembre.id().toString(),
                    noviembre.id().toString(), ajena.id().toString(), inexistente.toString());
            assertThat(planilla.fotoIds()).containsExactly(buena.id());
        }
    }

    @Test
    void laObraDebeSerLaDeLaPlanilla() {
        Planilla planilla = nueva(obraFija());

        assertThatThrownBy(() -> planilla.actualizarLineas(List.of(), Map.of(), obraFija()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
