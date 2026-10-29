package com.constructora.obras.avance.dominio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Foto de avance de una partida (005). El id lo crea el celular; el archivo está en el almacenamiento de objetos
 * con la clave fotos/{obraId}/{fotoId}.jpg. No es visible para el cliente hasta que se adjunta a una planilla
 * presentada (007): publicadaCliente nace en false.
 *
 * @param registradoPor sub del usuario que la registró
 * @param registradoEn  fecha y hora en que se tomó la foto (tomadaEn)
 */
public record FotoAvance(UUID id, UUID obraId, UUID partidaId, String claveArchivo, boolean publicadaCliente,
        UUID registradoPor, Instant registradoEn) {

    public static final String PARTIDA_NO_ES_DE_LA_OBRA = "PARTIDA_NO_ES_DE_LA_OBRA";
    public static final String CLAVE_ARCHIVO_INVALIDA = "CLAVE_ARCHIVO_INVALIDA";

    public FotoAvance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(obraId, "obraId");
        Objects.requireNonNull(partidaId, "partidaId");
        Objects.requireNonNull(claveArchivo, "claveArchivo");
        Objects.requireNonNull(registradoPor, "registradoPor");
        Objects.requireNonNull(registradoEn, "registradoEn");
    }

    /** Clave del archivo en el almacenamiento; la arma el servicio, no la app. */
    public static String claveArchivo(UUID obraId, UUID fotoId) {
        return "fotos/" + obraId + "/" + fotoId + ".jpg";
    }

    /**
     * Registra una foto nueva. Valida, en este orden, que la partida sea de la obra (PARTIDA_NO_ES_DE_LA_OBRA) y que
     * la clave sea la que entregó la URL de subida para ese id (CLAVE_ARCHIVO_INVALIDA).
     *
     * @param partidasDeLaObra ids de las partidas de la obra
     * @param tomadaEn         fecha y hora en que se tomó (se guarda como registradoEn, al microsegundo)
     */
    public static FotoAvance registrar(UUID id, UUID obraId, UUID partidaId, Set<UUID> partidasDeLaObra,
            String claveArchivo, Instant tomadaEn, UUID registradoPor) {
        Objects.requireNonNull(tomadaEn, "tomadaEn");
        if (!partidasDeLaObra.contains(partidaId)) {
            throw new ReglaNegocioException(PARTIDA_NO_ES_DE_LA_OBRA, "La partida elegida no pertenece a esta obra.",
                    List.of(new ErrorCampo("partidaId", "No es una partida de la obra")));
        }
        if (!claveArchivo(obraId, id).equals(claveArchivo)) {
            throw new ReglaNegocioException(CLAVE_ARCHIVO_INVALIDA, "No se pudo asociar el archivo a este registro.",
                    List.of(new ErrorCampo("claveArchivo", "Debe ser " + claveArchivo(obraId, id))));
        }
        return new FotoAvance(id, obraId, partidaId, claveArchivo, false, registradoPor,
                tomadaEn.truncatedTo(ChronoUnit.MICROS));
    }
}
