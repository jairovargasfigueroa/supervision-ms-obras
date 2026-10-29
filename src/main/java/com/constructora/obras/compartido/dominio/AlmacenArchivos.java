package com.constructora.obras.compartido.dominio;

import java.time.Duration;
import java.time.Instant;

/**
 * Puerto del almacenamiento de objetos (convenciones, «Archivos»): el servicio guarda solo la clave del archivo y
 * entrega URL firmadas para que la app suba y el ERP o el portal descarguen directo del almacenamiento. En la
 * aplicación es S3 (AlmacenArchivosS3); en las pruebas, un doble en memoria.
 */
public interface AlmacenArchivos {

    /**
     * URL firmada para subir (PUT) el archivo con esa clave. Solo acepta el tipo de contenido indicado.
     *
     * @throws AlmacenNoDisponibleException si el almacenamiento no está configurado
     */
    UrlFirmada urlSubida(String clave, String tipoContenido, Duration vigencia);

    /**
     * URL firmada para descargar (GET) el archivo con esa clave.
     *
     * @throws AlmacenNoDisponibleException si el almacenamiento no está configurado
     */
    UrlFirmada urlDescarga(String clave, Duration vigencia);

    /** URL firmada, el método HTTP con el que se usa y cuándo vence. */
    record UrlFirmada(String url, String metodo, Instant expiraEn) {
    }

    /** El almacenamiento no está configurado o no responde: la API responde 503 SERVICIO_NO_DISPONIBLE. */
    class AlmacenNoDisponibleException extends RuntimeException {

        public AlmacenNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
