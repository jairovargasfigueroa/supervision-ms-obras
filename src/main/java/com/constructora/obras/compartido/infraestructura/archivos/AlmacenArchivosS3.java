package com.constructora.obras.compartido.infraestructura.archivos;

import com.constructora.obras.compartido.dominio.AlmacenArchivos;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Adaptador de AlmacenArchivos sobre S3: firma URL de subida (PUT) y de descarga (GET) en el bucket configurado.
 * La firma se calcula aquí, sin llamar a AWS. Sin configuración (firmador vacío) lanza AlmacenNoDisponibleException.
 */
public class AlmacenArchivosS3 implements AlmacenArchivos, AutoCloseable {

    private final Optional<S3Presigner> firmador;
    private final String bucket;

    public AlmacenArchivosS3(Optional<S3Presigner> firmador, String bucket) {
        this.firmador = Objects.requireNonNull(firmador, "firmador");
        this.bucket = bucket == null ? null : bucket.trim();
    }

    /**
     * El tipo de contenido queda dentro de la firma: la subida debe enviar el mismo encabezado Content-Type
     * (convenciones, «Seguridad»: las URL de subida aceptan solo image/jpeg).
     */
    @Override
    public UrlFirmada urlSubida(String clave, String tipoContenido, Duration vigencia) {
        PutObjectPresignRequest pedido = PutObjectPresignRequest.builder()
                .signatureDuration(vigencia)
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(clave).contentType(tipoContenido)
                        .build())
                .build();
        return aUrl(firmador().presignPutObject(pedido));
    }

    @Override
    public UrlFirmada urlDescarga(String clave, Duration vigencia) {
        GetObjectPresignRequest pedido = GetObjectPresignRequest.builder()
                .signatureDuration(vigencia)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(clave).build())
                .build();
        return aUrl(firmador().presignGetObject(pedido));
    }

    @Override
    public void close() {
        firmador.ifPresent(S3Presigner::close);
    }

    private S3Presigner firmador() {
        return firmador.orElseThrow(() -> new AlmacenNoDisponibleException(
                "El almacenamiento de archivos no está configurado."));
    }

    private static UrlFirmada aUrl(PresignedRequest firmada) {
        return new UrlFirmada(firmada.url().toString(), firmada.httpRequest().method().name(),
                firmada.expiration());
    }
}
