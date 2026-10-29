package com.constructora.obras.obra.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObraTest {

    static final UUID RESIDENTE = UUID.fromString("a0000000-0000-4000-8000-000000000003");
    static final UUID MAESTRO = UUID.fromString("a0000000-0000-4000-8000-000000000004");
    static final UUID ALMACENERO = UUID.fromString("a0000000-0000-4000-8000-000000000005");
    static final UUID CLIENTE = UUID.fromString("a0000000-0000-4000-8000-000000000006");
    static final LocalDate INICIO = LocalDate.of(2026, 11, 1);

    static Asignaciones asignaciones() {
        return new Asignaciones(RESIDENTE, MAESTRO, ALMACENERO, CLIENTE);
    }

    static ContratoCopia contrato(String monto) {
        return ContratoCopia.nuevo(Dinero.de(new BigDecimal(monto)), 300, BigDecimal.TEN, BigDecimal.valueOf(7));
    }

    static Partida partida(String codigo, String cantidad, String precio, InsumoPrevisto... insumos) {
        return Partida.nueva(codigo, "Partida " + codigo, "m2", new BigDecimal(cantidad), new BigDecimal(precio),
                "nov-dic", List.of(insumos));
    }

    static Obra registrar(ContratoCopia contrato, Partida... partidas) {
        return Obra.registrar("Edificio Las Palmas", "Av. Banzer 4to anillo", INICIO, asignaciones(), contrato,
                List.of(partidas));
    }

    @Nested
    class Presupuesto {

        @Test
        void coincideConElMontoQuedaSinContrato() {
            // 850 × 420,50 = 357.425,00 · 1.785 × 500,00 = 892.500,00
            Obra obra = registrar(contrato("1249925.00"), partida("P-01", "850", "420.50"),
                    partida("P-02", "1785", "500.00"));

            assertThat(obra.estado()).isEqualTo(EstadoObra.SIN_CONTRATO);
            assertThat(obra.contrato().contratoId()).isNull();
            assertThat(obra.motivoRechazoContrato()).isNull();
            assertThat(obra.presupuesto()).isEqualTo(Dinero.de(new BigDecimal("1249925.00")));
            assertThat(obra.partidas()).hasSize(2);
        }

        @Test
        void aceptaUnaDiferenciaDeHastaUnCentavo() {
            assertThat(registrar(contrato("100.01"), partida("P-01", "1", "100.00")).estado())
                    .isEqualTo(EstadoObra.SIN_CONTRATO);
            assertThat(registrar(contrato("99.99"), partida("P-01", "1", "100.00")).estado())
                    .isEqualTo(EstadoObra.SIN_CONTRATO);
        }

        @Test
        void redondeaElTotalYNoCadaPartida() {
            // 3 × 0,333 = 0,999 y 3 × 0,334 = 1,002: el total 2,001 se redondea a 2,00
            assertThat(registrar(contrato("2.00"), partida("P-01", "0.333", "3.00"), partida("P-02", "0.334", "3.00"))
                    .presupuesto()).isEqualTo(Dinero.de(new BigDecimal("2.00")));
        }

        @Test
        void noCoincideMasAllaDeLaTolerancia() {
            assertThatThrownBy(() -> registrar(contrato("1250000.00"), partida("P-01", "850", "420.50")))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("PRESUPUESTO_NO_COINCIDE");
                        assertThat(error.getMessage()).isEqualTo(
                                "El presupuesto (Bs 357.425,00) no coincide con el monto del contrato (Bs 1.250.000,00)");
                    });
            assertThatThrownBy(() -> registrar(contrato("100.02"), partida("P-01", "1", "100.00")))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("PRESUPUESTO_NO_COINCIDE"));
        }
    }

    @Nested
    class Partidas {

        @Test
        void codigoRepetidoEsRechazado() {
            assertThatThrownBy(() -> registrar(contrato("200.00"), partida("P-01", "1", "100"),
                    partida("P-01", "1", "100")))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("CODIGO_PARTIDA_REPETIDO");
                        assertThat(error.getMessage()).contains("(P-01)");
                        assertThat(error.errores()).extracting(ReglaNegocioException.ErrorCampo::campo)
                                .containsExactly("partidas[1].codigo");
                    });
        }

        @Test
        void materialRepetidoEnLaMismaPartidaEsRechazado() {
            InsumoPrevisto cemento = new InsumoPrevisto("cemento", new BigDecimal("1.1"));
            assertThatThrownBy(() -> registrar(contrato("100.00"), partida("P-01", "1", "100", cemento,
                    new InsumoPrevisto("cemento", BigDecimal.ONE))))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("INSUMO_REPETIDO");
                        assertThat(error.getMessage()).isEqualTo("La partida P-01 tiene el mismo material más de una vez.");
                        assertThat(error.errores()).extracting(ReglaNegocioException.ErrorCampo::campo)
                                .containsExactly("partidas[0].insumos[1].materialId");
                    });
        }

        @Test
        void elMismoMaterialEnPartidasDistintasEstaPermitido() {
            InsumoPrevisto cemento = new InsumoPrevisto("cemento", new BigDecimal("1.1"));
            Obra obra = registrar(contrato("200.00"), partida("P-01", "1", "100", cemento),
                    partida("P-02", "1", "100", cemento));
            assertThat(obra.partidas()).allSatisfy(partida -> assertThat(partida.insumos()).hasSize(1));
        }

        @Test
        void sinPartidasNoSePuedeRegistrar() {
            assertThatThrownBy(() -> registrar(contrato("100.00"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Contrato {

        @Test
        void montoYPlazoDebenSerMayoresACero() {
            ContratoCopia invalido = ContratoCopia.nuevo(Dinero.CERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
            assertThatThrownBy(() -> registrar(invalido, partida("P-01", "1", "100")))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("CONTRATO_INVALIDO");
                        assertThat(error.errores()).extracting(ReglaNegocioException.ErrorCampo::campo)
                                .containsExactly("contrato.monto", "contrato.plazoDias");
                    });
        }

        @Test
        void porcentajesFueraDeRangoOQueSumanCienSonRechazados() {
            assertThat(errores(BigDecimal.valueOf(-1), BigDecimal.ZERO)).containsExactly("contrato.anticipoPct");
            assertThat(errores(BigDecimal.ZERO, BigDecimal.valueOf(101))).containsExactly("contrato.retencionPct");
            assertThat(errores(BigDecimal.valueOf(60), BigDecimal.valueOf(40))).containsExactly("contrato.retencionPct");
        }

        @Test
        void porcentajesQueSumanMenosDeCienSonValidos() {
            ContratoCopia valido = ContratoCopia.nuevo(Dinero.de(new BigDecimal("100")), 30, new BigDecimal("59.99"),
                    BigDecimal.valueOf(40));
            assertThat(registrar(valido, partida("P-01", "1", "100")).contrato().anticipoPct())
                    .isEqualByComparingTo("59.99");
        }

        private List<String> errores(BigDecimal anticipo, BigDecimal retencion) {
            ContratoCopia contrato = ContratoCopia.nuevo(Dinero.de(new BigDecimal("100")), 30, anticipo, retencion);
            try {
                registrar(contrato, partida("P-01", "1", "100"));
                throw new AssertionError("Se esperaba CONTRATO_INVALIDO");
            } catch (ReglaNegocioException error) {
                assertThat(error.codigo()).isEqualTo("CONTRATO_INVALIDO");
                return error.errores().stream().map(ReglaNegocioException.ErrorCampo::campo).toList();
            }
        }
    }

    @Nested
    class RespuestaDeFinanzas {

        @Test
        void contratoRegistradoPasaAEnEjecucion() {
            Obra obra = registrar(contrato("100.00"), partida("P-01", "1", "100"));
            obra.rechazarContrato("motivo anterior");
            UUID contratoId = UUID.randomUUID();

            assertThat(obra.registrarContrato(contratoId)).isTrue();
            assertThat(obra.estado()).isEqualTo(EstadoObra.EN_EJECUCION);
            assertThat(obra.contrato().contratoId()).isEqualTo(contratoId);
            assertThat(obra.motivoRechazoContrato()).isNull();
            assertThat(obra.registrarContrato(UUID.randomUUID())).isFalse();
            assertThat(obra.contrato().contratoId()).isEqualTo(contratoId);
        }

        @Test
        void contratoRechazadoGuardaElMotivoYSigueSinContrato() {
            Obra obra = registrar(contrato("100.00"), partida("P-01", "1", "100"));

            assertThat(obra.rechazarContrato("El monto del contrato debe ser mayor a 0")).isTrue();
            assertThat(obra.estado()).isEqualTo(EstadoObra.SIN_CONTRATO);
            assertThat(obra.motivoRechazoContrato()).isEqualTo("El monto del contrato debe ser mayor a 0");
        }

        @Test
        void unRechazoTardioNoCambiaUnaObraEnEjecucion() {
            Obra obra = registrar(contrato("100.00"), partida("P-01", "1", "100"));
            obra.registrarContrato(UUID.randomUUID());

            assertThat(obra.rechazarContrato("tarde")).isFalse();
            assertThat(obra.estado()).isEqualTo(EstadoObra.EN_EJECUCION);
            assertThat(obra.motivoRechazoContrato()).isNull();
        }
    }

    @Nested
    class Visibilidad {

        private final Obra obra = registrar(contrato("100.00"), partida("P-01", "1", "100"));

        @Test
        void adminYGerenteVenTodas() {
            assertThat(obra.esVisiblePara(new Usuario("otro", Set.of("admin")))).isTrue();
            assertThat(obra.esVisiblePara(new Usuario("otro", Set.of("gerente")))).isTrue();
        }

        @Test
        void losDemasSoloLasAsignadasConEseRol() {
            assertThat(obra.esVisiblePara(new Usuario(RESIDENTE.toString(), Set.of("residente")))).isTrue();
            assertThat(obra.esVisiblePara(new Usuario(MAESTRO.toString(), Set.of("maestro")))).isTrue();
            assertThat(obra.esVisiblePara(new Usuario(ALMACENERO.toString(), Set.of("almacenero")))).isTrue();
            assertThat(obra.esVisiblePara(new Usuario(CLIENTE.toString(), Set.of("cliente")))).isTrue();
            // Asignado como residente pero el rol efectivo es otro (por ejemplo, entró por el portal)
            assertThat(obra.esVisiblePara(new Usuario(RESIDENTE.toString(), Set.of("cliente")))).isFalse();
            assertThat(obra.esVisiblePara(new Usuario(UUID.randomUUID().toString(), Set.of("residente")))).isFalse();
            assertThat(obra.esVisiblePara(new Usuario(RESIDENTE.toString(), Set.of()))).isFalse();
        }
    }

    @Nested
    class AvanceYPlazo {

        private final Obra obra = registrar(contrato("100.00"), partida("P-01", "10", "10"));

        @Test
        void sinPlanillasAprobadasElAvanceEsCero() {
            Obra.Indicadores indicadores = obra.indicadores(Map.of(), INICIO);
            assertThat(indicadores.avancePct()).isEqualByComparingTo("0");
            assertThat(indicadores.avancePorPartida().get(obra.partidas().getFirst().id())).isEqualByComparingTo("0");
            assertThat(indicadores.plazoPct()).isEqualByComparingTo("0");
            assertThat(indicadores.enRiesgo()).isFalse();
        }

        @Test
        void elPlazoVaDeCeroACien() {
            assertThat(obra.indicadores(Map.of(), INICIO.minusDays(5)).plazoPct()).isEqualByComparingTo("0");
            assertThat(obra.indicadores(Map.of(), INICIO.plusDays(123)).plazoPct()).isEqualByComparingTo("41.00");
            assertThat(obra.indicadores(Map.of(), INICIO.plusDays(400)).plazoPct()).isEqualByComparingTo("100");
        }

        @Test
        void avanceConCantidadEjecutadaYRiesgoSiElPlazoLoSuperaPorMasDeDiezPuntos() {
            UUID partidaId = obra.partidas().getFirst().id();
            // 3 de 10 → avance 30 %; plazo 41 % → diferencia 11 puntos: en riesgo
            Obra.Indicadores indicadores = obra.indicadores(Map.of(partidaId, BigDecimal.valueOf(3)),
                    INICIO.plusDays(123));
            assertThat(indicadores.avancePct()).isEqualByComparingTo("30");
            assertThat(indicadores.avancePorPartida().get(partidaId)).isEqualByComparingTo("30");
            assertThat(indicadores.enRiesgo()).isTrue();
            // plazo 40 % → diferencia 10 puntos: no está en riesgo (debe ser > 10)
            assertThat(obra.indicadores(Map.of(partidaId, BigDecimal.valueOf(3)), INICIO.plusDays(120)).enRiesgo())
                    .isFalse();
        }
    }
}
