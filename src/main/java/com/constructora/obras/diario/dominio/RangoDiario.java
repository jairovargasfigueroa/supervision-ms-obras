package com.constructora.obras.diario.dominio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Fechas que pide GET /obras/{obraId}/diario (103), las dos incluidas. Sin hasta: hoy (La Paz); sin desde: los 30
 * días que terminan en hasta (hasta − 29). Como mucho 366 días (RANGO_INVALIDO).
 */
public record RangoDiario(LocalDate desde, LocalDate hasta) {

    public static final String RANGO_INVALIDO = "RANGO_INVALIDO";
    public static final int DIAS_POR_DEFECTO = 30;
    public static final int DIAS_MAXIMO = 366;

    public RangoDiario {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
    }

    /**
     * Arma el rango con los valores tal cual llegan en la consulta (vacíos o null: por defecto). Una fecha que no es
     * AAAA-MM-DD, o desde posterior a hasta: DATOS_INVALIDOS con el campo; más de 366 días: RANGO_INVALIDO.
     *
     * @param hoy fecha de hoy en America/La_Paz
     */
    public static RangoDiario de(String desde, String hasta, LocalDate hoy) {
        LocalDate fin = fecha("hasta", hasta);
        fin = fin == null ? hoy : fin;
        LocalDate inicio = fecha("desde", desde);
        inicio = inicio == null ? fin.minusDays(DIAS_POR_DEFECTO - 1L) : inicio;
        if (inicio.isAfter(fin)) {
            throw datosInvalidos("desde", "No puede ser posterior a hasta");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_MAXIMO) {
            throw new ReglaNegocioException(RANGO_INVALIDO,
                    "El rango de fechas es demasiado amplio (máximo " + DIAS_MAXIMO + " días).");
        }
        return new RangoDiario(inicio, fin);
    }

    private static LocalDate fecha(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            throw datosInvalidos(campo, "Debe ser una fecha AAAA-MM-DD");
        }
    }

    private static ReglaNegocioException datosInvalidos(String campo, String mensaje) {
        return new ReglaNegocioException(EntradaDiario.DATOS_INVALIDOS, "Algunos datos no son válidos.",
                List.of(new ErrorCampo(campo, mensaje)));
    }
}
