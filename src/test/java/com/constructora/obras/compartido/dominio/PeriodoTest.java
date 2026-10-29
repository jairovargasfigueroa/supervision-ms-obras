package com.constructora.obras.compartido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    @Test
    void seLeeYSeEscribeComoAaaaMm() {
        Periodo periodo = Periodo.de("2027-02");
        assertThat(periodo.toString()).isEqualTo("2027-02");
        assertThat(periodo.primerDia()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(Periodo.de(LocalDate.of(2027, 2, 28))).isEqualTo(periodo);
    }

    @Test
    void otroFormatoOUnMesQueNoExisteSeRechaza() {
        for (String texto : new String[] {null, "", "2027-2", "2027-13", "2027-00", "02-2027", "2027-02-01", " 2027-02"}) {
            assertThatThrownBy(() -> Periodo.de(texto)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
