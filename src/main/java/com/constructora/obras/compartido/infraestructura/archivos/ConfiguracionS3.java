package com.constructora.obras.compartido.infraestructura.archivos;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente S3 (AWS SDK v2) con las variables OBRAS_S3_REGION, OBRAS_S3_BUCKET, OBRAS_S3_ACCESS_KEY y
 * OBRAS_S3_SECRET_KEY (docs/entorno-local.md, «Archivos en desarrollo: S3 real»). Firmar una URL no usa la red.
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
            @Value("${obras.s3.secret-key}") String claveSecreta) {
        Optional<S3Presigner> firmador = firmador(region, bucket, claveAcceso, claveSecreta);
        if (firmador.isEmpty()) {
            log.warn("S3 sin configurar (OBRAS_S3_BUCKET, OBRAS_S3_ACCESS_KEY u OBRAS_S3_SECRET_KEY vacías): "
                    + "las rutas de fotos que firman URL responderán 503 SERVICIO_NO_DISPONIBLE");
        }
        return new AlmacenArchivosS3(firmador, bucket);
    }

    /** Firmador de URL, o vacío si falta la región, el bucket o alguna de las claves. */
    static Optional<S3Presigner> firmador(String region, String bucket, String claveAcceso, String claveSecreta) {
        if (vacio(region) || vacio(bucket) || vacio(claveAcceso) || vacio(claveSecreta)) {
            return Optional.empty();
        }
        return Optional.of(S3Presigner.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(claveAcceso.trim(), claveSecreta.trim())))
                .build());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
