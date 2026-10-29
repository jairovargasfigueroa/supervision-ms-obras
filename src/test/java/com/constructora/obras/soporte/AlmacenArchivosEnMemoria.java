package com.constructora.obras.soporte;

import com.constructora.obras.compartido.dominio.AlmacenArchivos;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Doble en memoria del puerto AlmacenArchivos (convenciones, «Archivos»: las pruebas no usan S3). Arma URL falsas
 * que muestran la clave, el método y la vigencia, y anota cada firma. Con {@link #noDisponible()} se comporta como
 * un S3 sin configurar.
 */
public class AlmacenArchivosEnMemoria implements AlmacenArchivos {

    public static final String BASE = "https://almacen.prueba/";

    /** Cada firma pedida: clave, método, tipo de contenido (solo en las subidas) y vigencia. */
    public record Firma(String clave, String metodo, String tipoContenido, Duration vigencia) {
    }

    private final List<Firma> firmas = new CopyOnWriteArrayList<>();
    private volatile boolean disponible = true;

    @Override
    public UrlFirmada urlSubida(String clave, String tipoContenido, Duration vigencia) {
        return firmar(new Firma(clave, "PUT", tipoContenido, vigencia));
    }

    @Override
    public UrlFirmada urlDescarga(String clave, Duration vigencia) {
        return firmar(new Firma(clave, "GET", null, vigencia));
    }

    public List<Firma> firmas() {
        return List.copyOf(firmas);
    }

    /** A partir de ahora responde como un almacenamiento sin configurar (503). */
    public void noDisponible() {
        disponible = false;
    }

    /** Vuelve al estado inicial: disponible y sin firmas anotadas (el contexto de Spring se comparte entre pruebas). */
    public void reiniciar() {
        disponible = true;
        firmas.clear();
    }

    private UrlFirmada firmar(Firma firma) {
        if (!disponible) {
            throw new AlmacenNoDisponibleException("Almacenamiento de prueba no disponible");
        }
        firmas.add(firma);
        return new UrlFirmada(BASE + firma.clave() + "?metodo=" + firma.metodo() + "&vigencia="
                + firma.vigencia().toSeconds(), firma.metodo(), Instant.now().plus(firma.vigencia()));
    }
}
