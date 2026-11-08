package com.constructora.obras.diario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RangoDiarioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);

    @Test
    void sinFechasSonLosUltimosTreintaDiasHastaHoy() {
        assertThat(RangoDiario.de(null, null, HOY)).isEqualTo(new RangoDiario(LocalDate.of(2026, 10, 17), HOY));
        assertThat(RangoDiario.de(" ", "", HOY)).isEqualTo(new RangoDiario(LocalDate.of(2026, 10, 17), HOY));
    }

    @Test
    void conUnaSolaFechaLaOtraVaPorDefecto() {
        assertThat(RangoDiario.de("2026-11-01", null, HOY))
                .isEqualTo(new RangoDiario(LocalDate.of(2026, 11, 1), HOY));
        assertThat(RangoDiario.de(null, "2026-10-30", HOY))
                .isEqualTo(new RangoDiario(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30)));
    }

    @Test
    void hastaTrescientosSesentaYSeisDias() {
        assertThat(RangoDiario.de("2026-01-01", "2027-01-01", HOY).desde()).isEqualTo(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> RangoDiario.de("2026-01-01", "2027-01-02", HOY))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RANGO_INVALIDO"));
    }

    @Test
    void fechasMalEscritasODesdePosteriorAHastaSonDatosInvalidos() {
        assertCampo(() -> RangoDiario.de("2026-13-01", null, HOY), "desde");
        assertCampo(() -> RangoDiario.de(null, "15/11/2026", HOY), "hasta");
        assertCampo(() -> RangoDiario.de("2026-11-16", "2026-11-15", HOY), "desde");
        assertThat(RangoDiario.de("2026-11-15", "2026-11-15", HOY).desde()).isEqualTo(HOY);
    }

    private static void assertCampo(Runnable accion, String campo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo("DATOS_INVALIDOS");
            assertThat(error.errores()).extracting(ErrorCampo::campo).containsExactly(campo);
        });
    }
}
