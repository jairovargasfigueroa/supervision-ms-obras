package com.constructora.obras.avance.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 301 · Validación de la detección de casco y chaleco (diseño, «Obras»). */
class DeteccionEppTest {

    @Test
    void validaConLosValoresDelDiseno() {
        DeteccionEpp.Validacion validacion = DeteccionEpp.validar(4, 1, 2, "epp-1.0.0");

        assertThat(validacion.descartada()).isFalse();
        assertThat(validacion.deteccion()).isEqualTo(new DeteccionEpp(4, 1, 2, "epp-1.0.0"));
        assertThat(validacion.motivo()).isNull();
    }

    @Test
    void enLosBordesEsValida() {
        assertThat(DeteccionEpp.validar(0, 0, 0, "e").descartada()).isFalse();
        assertThat(DeteccionEpp.validar(5, 5, 5, "e".repeat(40)).descartada()).isFalse();
    }

    @Test
    void sinCascoOSinChalecoMayoresQuePersonasSeDescartan() {
        assertThat(DeteccionEpp.validar(4, 5, 0, "epp-1.0.0").motivo()).isEqualTo("sinCasco es mayor que personas");
        assertThat(DeteccionEpp.validar(4, 0, 5, "epp-1.0.0").motivo())
                .isEqualTo("sinChaleco es mayor que personas");
    }

    @Test
    void conteosNegativosSeDescartan() {
        assertThat(DeteccionEpp.validar(-1, 0, 0, "epp-1.0.0").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, -1, 0, "epp-1.0.0").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, 0, -1, "epp-1.0.0").descartada()).isTrue();
    }

    @Test
    void laVersionDebeTenerDe1A40Caracteres() {
        assertThat(DeteccionEpp.validar(1, 0, 0, "").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, 0, 0, "   ").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, 0, 0, "e".repeat(41)).descartada()).isTrue();
    }

    @Test
    void unCampoQueFaltaONoEsDelTipoEsperadoSeDescarta() {
        assertThat(DeteccionEpp.validar(null, 0, 0, "epp-1.0.0").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, null, 0, "epp-1.0.0").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, 0, null, "epp-1.0.0").descartada()).isTrue();
        assertThat(DeteccionEpp.validar(1, 0, 0, null).motivo()).isEqualTo("faltan campos o no son del tipo esperado");
    }

    @Test
    void elObjetoDeValorNoSeCreaInvalido() {
        assertThatThrownBy(() -> new DeteccionEpp(1, 2, 0, "epp-1.0.0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeteccionEpp(1, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
