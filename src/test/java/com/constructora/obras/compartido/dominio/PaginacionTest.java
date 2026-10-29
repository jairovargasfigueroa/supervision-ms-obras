package com.constructora.obras.compartido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PaginacionTest {

    private static final Set<String> ORDENES = Set.of("registradoEn");

    @Test
    void porDefectoPaginaUnoDeVeinteEnOrdenDescendente() {
        assertThat(Paginacion.de(null, null, null, null, ORDENES, "registradoEn"))
                .isEqualTo(new Paginacion(1, 20, "registradoEn", false));
        assertThat(Paginacion.de(3, 100, "registradoEn", "asc", ORDENES, "registradoEn"))
                .isEqualTo(new Paginacion(3, 100, "registradoEn", true));
    }

    @Test
    void valoresFueraDeRangoSonPaginacionInvalida() {
        invalida(1, 101, null, null, "tamano");
        invalida(1, 0, null, null, "tamano");
        invalida(0, 20, null, null, "pagina");
        invalida(1, 20, "nombre", null, "orden");
        invalida(1, 20, null, "arriba", "direccion");
    }

    private static void invalida(Integer pagina, Integer tamano, String orden, String direccion, String campo) {
        assertThatThrownBy(() -> Paginacion.de(pagina, tamano, orden, direccion, ORDENES, "registradoEn"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("PAGINACION_INVALIDA");
                    assertThat(error.errores()).singleElement()
                            .satisfies(error2 -> assertThat(error2.campo()).isEqualTo(campo));
                });
    }
}
