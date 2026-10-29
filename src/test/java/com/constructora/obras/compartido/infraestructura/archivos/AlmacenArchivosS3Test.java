package com.constructora.obras.compartido.infraestructura.archivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.constructora.obras.compartido.dominio.AlmacenArchivos.AlmacenNoDisponibleException;
import com.constructora.obras.compartido.dominio.AlmacenArchivos.UrlFirmada;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Firma de URL con el SDK, sin red: credenciales y bucket ficticios. */
class AlmacenArchivosS3Test {

    private static final String BUCKET = "supervision-prueba-ficticio";

    private final AlmacenArchivosS3 almacen = new AlmacenArchivosS3(
            ConfiguracionS3.firmador("sa-east-1", BUCKET, "AKIAFICTICIA000000", "secreta-ficticia"), BUCKET);

    @AfterEach
    void cerrar() {
        almacen.close();
    }

    @Test
    void urlDeSubidaLlevaLaClaveElMetodoPutElTipoYElVencimiento() {
        UUID obraId = UUID.randomUUID();
        UUID fotoId = UUID.randomUUID();
        String clave = "fotos/" + obraId + "/" + fotoId + ".jpg";

        Instant antes = Instant.now();
        UrlFirmada firmada = almacen.urlSubida(clave, "image/jpeg", Duration.ofMinutes(10));

        URI url = URI.create(firmada.url());
        assertThat(firmada.metodo()).isEqualTo("PUT");
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo(BUCKET + ".s3.sa-east-1.amazonaws.com");
        assertThat(url.getPath()).isEqualTo("/" + clave);
        assertThat(url.getQuery()).contains("X-Amz-Expires=600").contains("X-Amz-Signature=")
                .contains("X-Amz-Credential=AKIAFICTICIA000000/");
        // El tipo de contenido es parte de la firma: la app debe subir con Content-Type image/jpeg
        assertThat(url.getQuery()).containsPattern("X-Amz-SignedHeaders=[^&]*content-type");
        assertThat(firmada.expiraEn()).isCloseTo(antes.plus(Duration.ofMinutes(10)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void urlDeDescargaEsGetConSuVencimiento() {
        String clave = "fotos/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".jpg";

        UrlFirmada firmada = almacen.urlDescarga(clave, Duration.ofMinutes(30));

        URI url = URI.create(firmada.url());
        assertThat(firmada.metodo()).isEqualTo("GET");
        assertThat(url.getPath()).isEqualTo("/" + clave);
        assertThat(url.getQuery()).contains("X-Amz-Expires=1800");
        assertThat(firmada.expiraEn()).isCloseTo(Instant.now().plus(Duration.ofMinutes(30)),
                within(5, ChronoUnit.SECONDS));
    }

    @Test
    void sinBucketOSinClavesNoHayFirmadorYResponde503() {
        assertThat(ConfiguracionS3.firmador("sa-east-1", "", "clave", "secreta")).isEmpty();
        assertThat(ConfiguracionS3.firmador("sa-east-1", BUCKET, "", "secreta")).isEmpty();
        assertThat(ConfiguracionS3.firmador("sa-east-1", BUCKET, "clave", " ")).isEmpty();
        assertThat(ConfiguracionS3.firmador(null, BUCKET, "clave", "secreta")).isEmpty();

        AlmacenArchivosS3 sinConfigurar = new AlmacenArchivosS3(Optional.empty(), "");
        assertThatThrownBy(() -> sinConfigurar.urlSubida("fotos/a/b.jpg", "image/jpeg", Duration.ofMinutes(10)))
                .isInstanceOf(AlmacenNoDisponibleException.class);
        assertThatThrownBy(() -> sinConfigurar.urlDescarga("fotos/a/b.jpg", Duration.ofMinutes(30)))
                .isInstanceOf(AlmacenNoDisponibleException.class);
    }
}
