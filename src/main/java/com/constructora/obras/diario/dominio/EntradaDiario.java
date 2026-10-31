package com.constructora.obras.diario.dominio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada del diario de obra (103): el clima y lo que pasó en la obra un día, anotado por el maestro. El id lo crea
 * el celular. Hay una sola entrada por obra y día (la restricción única de entrada_diario lo garantiza).
 *
 * @param registradoPor sub del maestro que la registró
 * @param registradoEn  momento en que el servidor la recibió
 */
public record EntradaDiario(UUID id, UUID obraId, LocalDate fecha, Clima clima, String texto, UUID registradoPor,
        Instant registradoEn) {

    public static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    public static final String OBRA_NO_EN_EJECUCION = "OBRA_NO_EN_EJECUCION";
    public static final String FECHA_DIARIO_INVALIDA = "FECHA_DIARIO_INVALIDA";
    public static final String DIARIO_DIA_YA_REGISTRADO = "DIARIO_DIA_YA_REGISTRADO";
    /** Largo del texto (103, criterio 1), contado sin los espacios de los extremos. */
    public static final int TEXTO_MINIMO = 10;
    public static final int TEXTO_MAXIMO = 2000;

    public EntradaDiario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(obraId, "obraId");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(clima, "clima");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(registradoPor, "registradoPor");
        Objects.requireNonNull(registradoEn, "registradoEn");
    }

    /** Clima y texto ya validados; el texto va sin los espacios de los extremos. */
    public record Contenido(Clima clima, String texto) {
    }

    /**
     * Formato del clima y del texto (400 DATOS_INVALIDOS, con un elemento de errores por campo): el clima es uno de
     * los cuatro valores y el texto tiene de 10 a 2000 caracteres sin contar los espacios de los extremos.
     */
    public static Contenido validarContenido(String clima, String texto) {
        List<ErrorCampo> errores = new ArrayList<>();
        Clima valor = Clima.de(clima).orElse(null);
        if (valor == null) {
            errores.add(new ErrorCampo("clima", "Debe ser SOLEADO, NUBLADO, LLUVIA_LEVE o LLUVIA_FUERTE"));
        }
        String limpio = texto == null ? "" : texto.strip();
        int largo = limpio.codePointCount(0, limpio.length());
        if (largo < TEXTO_MINIMO || largo > TEXTO_MAXIMO) {
            errores.add(new ErrorCampo("texto", "Debe tener entre " + TEXTO_MINIMO + " y " + TEXTO_MAXIMO
                    + " caracteres"));
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(DATOS_INVALIDOS, "Algunos datos no son válidos.", errores);
        }
        return new Contenido(valor, limpio);
    }

    /**
     * Registra una entrada nueva. Valida, en este orden, que la obra esté EN_EJECUCION (OBRA_NO_EN_EJECUCION) y que la
     * fecha no sea posterior a hoy en America/La_Paz ni anterior al inicio de la obra (FECHA_DIARIO_INVALIDA).
     *
     * @param recibidaEn momento en que llegó al servidor: da el «hoy» de La Paz y se guarda como registradoEn
     */
    public static EntradaDiario registrar(UUID id, Obra obra, LocalDate fecha, Contenido contenido,
            UUID registradoPor, Instant recibidaEn) {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(recibidaEn, "recibidaEn");
        if (obra.estado() != EstadoObra.EN_EJECUCION) {
            throw new ReglaNegocioException(OBRA_NO_EN_EJECUCION, "La obra no está en ejecución");
        }
        LocalDate hoy = recibidaEn.atZone(Reloj.ZONA_NEGOCIO).toLocalDate();
        if (fecha.isAfter(hoy)) {
            throw fechaInvalida("No puede ser posterior a hoy (" + hoy + ")");
        }
        if (fecha.isBefore(obra.fechaInicio())) {
            throw fechaInvalida("No puede ser anterior al inicio de la obra (" + obra.fechaInicio() + ")");
        }
        return new EntradaDiario(id, obra.id(), fecha, contenido.clima(), contenido.texto(), registradoPor,
                recibidaEn.truncatedTo(ChronoUnit.MICROS));
    }

    private static ReglaNegocioException fechaInvalida(String mensaje) {
        return new ReglaNegocioException(FECHA_DIARIO_INVALIDA, "La fecha no es válida para esta obra",
                List.of(new ErrorCampo("fecha", mensaje)));
    }
}
