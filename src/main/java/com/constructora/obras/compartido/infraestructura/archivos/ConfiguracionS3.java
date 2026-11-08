package com.constructora.obras.compartido.infraestructura.archivos;

import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente S3 (AWS SDK v2) con las variables OBRAS_S3_REGION, OBRAS_S3_BUCKET, OBRAS_S3_ACCESS_KEY y
 * OBRAS_S3_SECRET_KEY (docs/entorno-local.md, «Archivos en desarrollo: S3 real»). Firmar una URL no usa la red.
 *
 * <p>Con OBRAS_S3_ENDPOINT (601) firma para un S3 compatible, por ejemplo el MinIO de la producción local, con rutas de
 * estilo path ({@code <endpoint>/<bucket>/<clave>}); vacío, firma para Amazon S3.
 *
 * <p>Si falta el bucket o alguna clave, el servicio arranca igual: las rutas que necesitan el almacenamiento
 * responden 503 SERVICIO_NO_DISPONIBLE (AlmacenArchivosS3) y el resto funciona.
 */
@Configuration
public class ConfiguracionS3 {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionS3.class);

    @Bean
    AlmacenArchivosS3 almacenArchivosS3(@Value("${obras.s3.region}") String region,
            @Value("${obras.s3.bucket}") String bucket,
            @Value("${obras.s3.access-key}") String claveAcceso,
            @Value("${obras.s3.secret-key}") String claveSecreta,
            @Value("${obras.s3.endpoint:}") String endpoint) {
        Optional<S3Presigner> firmador = firmador(region, bucket, claveAcceso, claveSecreta, endpoint);
        if (firmador.isEmpty()) {
            log.warn("S3 sin configurar (OBRAS_S3_BUCKET, OBRAS_S3_ACCESS_KEY u OBRAS_S3_SECRET_KEY vacías): "
                    + "las rutas de fotos que firman URL responderán 503 SERVICIO_NO_DISPONIBLE");
        }
        return new AlmacenArchivosS3(firmador, bucket);
    }

    /** Firmador de URL para Amazon S3, o vacío si falta la región, el bucket o alguna de las claves. */
    static Optional<S3Presigner> firmador(String region, String bucket, String claveAcceso, String claveSecreta) {
        return firmador(region, bucket, claveAcceso, claveSecreta, null);
    }

    /**
     * Firmador de URL, o vacío si falta la región, el bucket o alguna de las claves. Con {@code endpoint} (URL absoluta
     * de un S3 compatible) firma contra esa dirección con rutas de estilo path.
     */
    static Optional<S3Presigner> firmador(String region, String bucket, String claveAcceso, String claveSecreta,
            String endpoint) {
        if (vacio(region) || vacio(bucket) || vacio(claveAcceso) || vacio(claveSecreta)) {
            return Optional.empty();
        }
        S3Presigner.Builder constructor = S3Presigner.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(claveAcceso.trim(), claveSecreta.trim())));
        if (!vacio(endpoint)) {
            constructor.endpointOverride(URI.create(endpoint.trim()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return Optional.of(constructor.build());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
