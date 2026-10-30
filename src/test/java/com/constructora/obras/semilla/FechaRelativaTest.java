package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.Reloj;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 011 · Resolvedor de fechas relativas con la tabla del diseño (D = 2026-10-28). */
class FechaRelativaTest {

    private final FechaRelativa fechas = new FechaRelativa(LocalDate.parse("2026-10-28"));

    @Test
    void tablaDelDiseno() {
        assertThat(fechas.fecha("D-120")).isEqualTo(LocalDate.parse("2026-06-30"));
        assertThat(fechas.fecha("D+18")).isEqualTo(LocalDate.parse("2026-11-15"));
        assertThat(fechas.periodo("M-3")).isEqualTo(YearMonth.parse("2026-07"));
        assertThat(fechas.periodo("M-3").atDay(1)).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(fechas.instante("M-1/09 10:00")).isEqualTo(Instant.parse("2026-09-09T14:00:00Z"));
        assertThat(fechas.instante("D-2 11:00")).isEqualTo(Instant.parse("2026-10-26T15:00:00Z"));
    }

    @Test
    void dSolaYElMesDeD() {
        assertThat(fechas.fecha("D")).isEqualTo(LocalDate.parse("2026-10-28"));
        assertThat(fechas.periodo("M0")).isEqualTo(YearMonth.parse("2026-10"));
        assertThat(fechas.periodo("M+3")).isEqualTo(YearMonth.parse("2027-01"));
        assertThat(fechas.instante("D 10:00")).isEqualTo(Instant.parse("2026-10-28T14:00:00Z"));
    }

    @Test
    void mesesPrevistosConAbreviaturasEnEspanol() {
        assertThat(fechas.meses("M-3")).isEqualTo("jul");
        assertThat(fechas.meses("M-3..M-2")).isEqualTo("jul-ago");
        assertThat(fechas.meses("M-2..M+3")).isEqualTo("ago-ene");
        assertThat(fechas.meses("M+1..M+7")).isEqualTo("nov-may");
    }

    @Test
    void cruzaElAnio() {
        FechaRelativa enero = new FechaRelativa(LocalDate.parse("2027-01-20"));
        assertThat(enero.fecha("D-40")).isEqualTo(LocalDate.parse("2026-12-11"));
        assertThat(enero.periodo("M-1")).isEqualTo(YearMonth.parse("2026-12"));
        assertThat(enero.instante("M-3/05 10:00")).isEqualTo(Instant.parse("2026-10-05T14:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "D-", "D - 120", "d-120", "M", "M-", "M-1/09", "M-1/9 10:00", "D-2 11", "D-2 25:00",
            "M-1/09 10:60", "X+1", "M-1..", "M-1..M-2"})
    void expresionesMalFormadasFallan(String expresion) {
        assertThatThrownBy(() -> fechas.fecha(expresion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fechas.periodo(expresion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fechas.instante(expresion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fechas.meses(expresion)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M-1/01 10:00", "M-1/28 10:00", "M-1/31 10:00", "M0/00 10:00"})
    void diasFueraDe2a27Fallan(String expresion) {
        assertThatThrownBy(() -> fechas.instante(expresion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("02 a 27");
    }

    @Test
    void diasEnElBordeValen() {
        assertThat(fechas.instante("M-1/02 10:00")).isEqualTo(Instant.parse("2026-09-02T14:00:00Z"));
        assertThat(fechas.instante("M-1/27 10:00")).isEqualTo(Instant.parse("2026-09-27T14:00:00Z"));
    }

    @Test
    void fechaDeCargaDesdeSemillaFechaOHoyEnLaPaz() {
        // 2026-10-29 02:00 UTC es todavía 28 de octubre en La Paz (UTC-4)
        Reloj reloj = () -> Instant.parse("2026-10-29T02:00:00Z");
        assertThat(FechaRelativa.desde("2026-10-20", reloj).d()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(FechaRelativa.desde(" 2026-10-20 ", reloj).d()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(FechaRelativa.desde("", reloj).d()).isEqualTo(LocalDate.parse("2026-10-28"));
        assertThat(FechaRelativa.desde(null, reloj).d()).isEqualTo(LocalDate.parse("2026-10-28"));
        assertThatThrownBy(() -> FechaRelativa.desde("28/10/2026", reloj))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEMILLA_FECHA");
    }
}
