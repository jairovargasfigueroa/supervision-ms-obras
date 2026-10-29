package com.constructora.obras.avance.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FotoAvanceTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID PARTIDA = UUID.randomUUID();
    private static final UUID FOTO = UUID.randomUUID();
    private static final UUID MAESTRO = UUID.randomUUID();

    @Test
    void laClaveLaArmaElServicioConObraYFoto() {
        assertThat(FotoAvance.claveArchivo(OBRA, FOTO)).isEqualTo("fotos/" + OBRA + "/" + FOTO + ".jpg");
    }

    @Test
    void registraConLaFechaEnQueSeTomoYSinPublicarParaElCliente() {
        Instant tomadaEn = Instant.parse("2026-11-15T15:20:00.123456789Z");

        FotoAvance foto = FotoAvance.registrar(FOTO, OBRA, PARTIDA, Set.of(PARTIDA),
                FotoAvance.claveArchivo(OBRA, FOTO), tomadaEn, MAESTRO);

        assertThat(foto.registradoEn()).isEqualTo(Instant.parse("2026-11-15T15:20:00.123456Z"));
        assertThat(foto.registradoPor()).isEqualTo(MAESTRO);
        assertThat(foto.publicadaCliente()).isFalse();
        assertThat(foto.partidaId()).isEqualTo(PARTIDA);
    }

    @Test
    void laPartidaDebeSerDeLaObra() {
        assertThatThrownBy(() -> FotoAvance.registrar(FOTO, OBRA, UUID.randomUUID(), Set.of(PARTIDA),
                FotoAvance.claveArchivo(OBRA, FOTO), Instant.now(), MAESTRO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("PARTIDA_NO_ES_DE_LA_OBRA");
                    assertThat(error.errores()).singleElement()
                            .satisfies(campo -> assertThat(campo.campo()).isEqualTo("partidaId"));
                });
    }

    @Test
    void laClaveDebeSerLaDeEsaObraYEsaFoto() {
        for (String clave : new String[] {"fotos/" + UUID.randomUUID() + "/" + FOTO + ".jpg",
                "fotos/" + OBRA + "/" + UUID.randomUUID() + ".jpg", "fotos/" + OBRA + "/" + FOTO + ".png",
                "otra/" + OBRA + "/" + FOTO + ".jpg"}) {
            assertThatThrownBy(() -> FotoAvance.registrar(FOTO, OBRA, PARTIDA, Set.of(PARTIDA), clave, Instant.now(),
                    MAESTRO))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("CLAVE_ARCHIVO_INVALIDA"));
        }
    }

    @Test
    void laPartidaSeValidaAntesQueLaClave() {
        assertThatThrownBy(() -> FotoAvance.registrar(FOTO, OBRA, UUID.randomUUID(), Set.of(PARTIDA), "mala",
                Instant.now(), MAESTRO))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("PARTIDA_NO_ES_DE_LA_OBRA"));
    }
}
