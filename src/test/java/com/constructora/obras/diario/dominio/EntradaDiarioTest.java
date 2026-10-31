package com.constructora.obras.diario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.diario.dominio.EntradaDiario.Contenido;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 103 · Reglas del diario (diseño, «Pruebas»): bordes del texto, clima, hoy en La Paz, fecha futura y anterior al inicio. */
class EntradaDiarioTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    /** 23:30 del 15/11 en La Paz (UTC-4): en UTC ya es el 16. */
    private static final Instant NOCHE_DEL_15 = Instant.parse("2026-11-16T03:30:00Z");
    private static final Contenido CONTENIDO = new Contenido(Clima.LLUVIA_LEVE, "Se vació la losa del 2do piso.");

    private final UUID maestro = UUID.randomUUID();
    private final Obra obra = obra(EstadoObra.EN_EJECUCION);

    @Test
    void elTextoVaDeDiezADosMilCaracteresSinContarLosEspaciosDeLosExtremos() {
        assertThat(EntradaDiario.validarContenido("SOLEADO", "  " + "a".repeat(10) + "  ").texto())
                .isEqualTo("a".repeat(10));
        assertThat(EntradaDiario.validarContenido("SOLEADO", "a".repeat(2000)).texto()).hasSize(2000);

        assertCampos(() -> EntradaDiario.validarContenido("SOLEADO", "  " + "a".repeat(9) + "   "), "texto");
        assertCampos(() -> EntradaDiario.validarContenido("SOLEADO", "a".repeat(2001)), "texto");
        assertCampos(() -> EntradaDiario.validarContenido("SOLEADO", null), "texto");
    }

    @Test
    void elClimaEsUnoDeLosCuatroValores() {
        assertThat(List.of("SOLEADO", "NUBLADO", "LLUVIA_LEVE", "LLUVIA_FUERTE"))
                .allSatisfy(clima -> assertThat(EntradaDiario.validarContenido(clima, "Texto del día").clima())
                        .hasToString(clima));

        assertCampos(() -> EntradaDiario.validarContenido("soleado", "Texto del día"), "clima");
        assertCampos(() -> EntradaDiario.validarContenido(null, "corto"), "clima", "texto");
    }

    @Test
    void laFechaDeHoyEsLaDeLaPazAunqueEnUtcYaSeaManana() {
        EntradaDiario entrada = EntradaDiario.registrar(UUID.randomUUID(), obra, LocalDate.of(2026, 11, 15),
                CONTENIDO, maestro, NOCHE_DEL_15);

        assertThat(entrada.fecha()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(entrada.registradoEn()).isEqualTo(NOCHE_DEL_15);
        assertThat(entrada.registradoPor()).isEqualTo(maestro);
        assertThat(entrada.obraId()).isEqualTo(obra.id());

        // El 16 todavía es futuro en La Paz
        assertThatThrownBy(() -> EntradaDiario.registrar(UUID.randomUUID(), obra, LocalDate.of(2026, 11, 16),
                CONTENIDO, maestro, NOCHE_DEL_15))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("FECHA_DIARIO_INVALIDA");
                    assertThat(error.errores()).extracting(ErrorCampo::campo).containsExactly("fecha");
                });
    }

    @Test
    void elInicioDeLaObraSeAceptaYElDiaAnteriorNo() {
        assertThat(EntradaDiario.registrar(UUID.randomUUID(), obra, INICIO, CONTENIDO, maestro, NOCHE_DEL_15)
                .fecha()).isEqualTo(INICIO);

        assertThatThrownBy(() -> EntradaDiario.registrar(UUID.randomUUID(), obra, INICIO.minusDays(1), CONTENIDO,
                maestro, NOCHE_DEL_15))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("FECHA_DIARIO_INVALIDA"));
    }

    @Test
    void laObraDebeEstarEnEjecucionYSeCompruebaAntesQueLaFecha() {
        for (EstadoObra estado : List.of(EstadoObra.SIN_CONTRATO, EstadoObra.CONCLUIDA)) {
            assertThatThrownBy(() -> EntradaDiario.registrar(UUID.randomUUID(), obra(estado),
                    LocalDate.of(2027, 1, 1), CONTENIDO, maestro, NOCHE_DEL_15))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_EN_EJECUCION"));
        }
    }

    @Test
    void registradoEnSeGuardaAlMicrosegundo() {
        Instant conNanos = Instant.parse("2026-11-15T15:20:00.123456789Z");

        assertThat(EntradaDiario.registrar(UUID.randomUUID(), obra, LocalDate.of(2026, 11, 15), CONTENIDO, maestro,
                conNanos).registradoEn()).isEqualTo(Instant.parse("2026-11-15T15:20:00.123456Z"));
    }

    private static void assertCampos(Runnable accion, String... campos) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo("DATOS_INVALIDOS");
            assertThat(error.errores()).extracting(ErrorCampo::campo).containsExactly(campos);
        });
    }

    private static Obra obra(EstadoObra estado) {
        Partida partida = Partida.nueva("P-01", "Losa", "m2", BigDecimal.TEN, BigDecimal.valueOf(100), null,
                List.of());
        return Obra.reconstruir(UUID.randomUUID(), "Obra de prueba", "Santa Cruz", INICIO, estado,
                new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                ContratoCopia.nuevo(Dinero.de(new BigDecimal("1000")), 100, BigDecimal.TEN, BigDecimal.valueOf(7)),
                null, List.of(partida));
    }
}
