package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.semilla.GuardaSemilla.SemillaRechazadaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** 011 · Guardas: la semilla se niega con el perfil prod o con una base que no es local. */
class GuardaSemillaTest {

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost:5433/obras_db", "jdbc:postgresql://127.0.0.1/obras_db",
            "jdbc:postgresql://LOCALHOST:5433/obras_db?ssl=false", "jdbc:postgresql://[::1]:5433/obras_db",
            "jdbc:postgresql://postgres:5432/obras_db", "jdbc:postgresql://localhost?loggerLevel=OFF"})
    void aceptaBasesLocales(String url) {
        assertThatCode(() -> GuardaSemilla.verificar(false, url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://obras.abc123.sa-east-1.rds.amazonaws.com:5432/obras_db",
            "jdbc:postgresql://10.0.0.5:5432/obras_db", "jdbc:postgresql://localhost:5433,db.ejemplo.com:5432/obras",
            "jdbc:postgresql://localhost.ejemplo.com/obras", "jdbc:mysql://localhost/obras", "", "jdbc:postgresql:obras"})
    void rechazaBasesQueNoSonLocales(String url) {
        assertThatThrownBy(() -> GuardaSemilla.verificar(false, url))
                .isInstanceOf(SemillaRechazadaException.class)
                .hasMessageContaining("base local");
    }

    @Test
    void rechazaElPerfilProdAunqueLaBaseSeaLocal() {
        assertThatThrownBy(() -> GuardaSemilla.verificar(true, "jdbc:postgresql://localhost:5433/obras_db"))
                .isInstanceOf(SemillaRechazadaException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void elRechazoTerminaConCodigo2() {
        assertThat(new SemillaRechazadaException("x").getExitCode()).isEqualTo(2);
        assertThat(GuardaSemilla.host("jdbc:postgresql://postgres:5432/obras_db")).contains("postgres");
        assertThat(GuardaSemilla.host(null)).isEmpty();
    }
}
