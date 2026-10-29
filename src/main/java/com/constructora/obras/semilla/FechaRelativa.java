package com.constructora.obras.semilla;

import com.constructora.obras.compartido.dominio.Reloj;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolvedor de las fechas relativas a la fecha de carga D (docs/datos-prueba.md, sección 1; 011, diseño):
 * <ul>
 * <li>{@code D}, {@code D-120}, {@code D+18}: fecha, n días antes o después de D.</li>
 * <li>{@code M0}, {@code M-3}, {@code M+2}: periodo (mes) k meses antes o después del mes de D.</li>
 * <li>{@code M-1/09 10:00}: día 9 del mes {@code M-1} a esa hora de La Paz; el día va de 02 a 27.</li>
 * <li>{@code D-2 11:00}: ese día a esa hora de La Paz.</li>
 * <li>{@code M-3..M-2} o {@code M-3}: meses previstos de una partida con las abreviaturas en español
 * ({@code jul-ago}, {@code jul}).</li>
 * </ul>
 * Una expresión mal formada o un día fuera de 2–27 lanza {@link IllegalArgumentException}: la carga se detiene, no
 * se adivina.
 */
public final class FechaRelativa {

    /** Zona del negocio: las horas de las expresiones son de La Paz. */
    public static final ZoneId ZONA = Reloj.ZONA_NEGOCIO;
    private static final int DIA_MINIMO = 2;
    private static final int DIA_MAXIMO = 27;
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct",
            "nov", "dic"};

    private static final Pattern FECHA = Pattern.compile("D([+-]\\d{1,4})?");
    private static final Pattern PERIODO = Pattern.compile("M(0|[+-]\\d{1,3})");
    private static final Pattern DIA_DEL_PERIODO = Pattern.compile("M(0|[+-]\\d{1,3})/(\\d{2}) (\\d{2}:\\d{2})");
    private static final Pattern FECHA_CON_HORA = Pattern.compile("D([+-]\\d{1,4})? (\\d{2}:\\d{2})");
    private static final Pattern MESES_PREVISTOS = Pattern.compile("(M(?:0|[+-]\\d{1,3}))(?:\\.\\.(M(?:0|[+-]\\d{1,3})))?");

    private final LocalDate d;

    public FechaRelativa(LocalDate d) {
        this.d = Objects.requireNonNull(d, "d");
    }

    /**
     * Fecha de carga D: la de {@code semilla.fecha} ({@code SEMILLA_FECHA}, AAAA-MM-DD) si viene, o la de hoy en La
     * Paz. infra la calcula una sola vez y la pasa a los tres servicios.
     */
    public static FechaRelativa desde(String semillaFecha, Reloj reloj) {
        if (semillaFecha == null || semillaFecha.isBlank()) {
            return new FechaRelativa(reloj.hoy());
        }
        try {
            return new FechaRelativa(LocalDate.parse(semillaFecha.strip()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("SEMILLA_FECHA debe tener el formato AAAA-MM-DD (llegó «"
                    + semillaFecha + "»)", e);
        }
    }

    /** Fecha de carga D. */
    public LocalDate d() {
        return d;
    }

    /** {@code D}, {@code D-n} o {@code D+n}. */
    public LocalDate fecha(String expresion) {
        Matcher m = coincidir(FECHA, expresion, "una fecha (D, D-n o D+n)");
        return d.plusDays(desplazamiento(m.group(1)));
    }

    /** {@code M0}, {@code M-k} o {@code M+k}. */
    public YearMonth periodo(String expresion) {
        Matcher m = coincidir(PERIODO, expresion, "un periodo (M0, M-k o M+k)");
        return YearMonth.from(d).plusMonths(desplazamiento(m.group(1)));
    }

    /** {@code M-k/dd hh:mm} o {@code D-n hh:mm}, a esa hora de La Paz. */
    public Instant instante(String expresion) {
        Objects.requireNonNull(expresion, "expresion");
        Matcher delPeriodo = DIA_DEL_PERIODO.matcher(expresion);
        if (delPeriodo.matches()) {
            int dia = Integer.parseInt(delPeriodo.group(2));
            if (dia < DIA_MINIMO || dia > DIA_MAXIMO) {
                throw new IllegalArgumentException("El día de «" + expresion + "» debe ir de 02 a 27");
            }
            YearMonth mes = YearMonth.from(d).plusMonths(desplazamiento(delPeriodo.group(1)));
            return mes.atDay(dia).atTime(hora(delPeriodo.group(3), expresion)).atZone(ZONA).toInstant();
        }
        Matcher conHora = FECHA_CON_HORA.matcher(expresion);
        if (conHora.matches()) {
            return d.plusDays(desplazamiento(conHora.group(1))).atTime(hora(conHora.group(2), expresion))
                    .atZone(ZONA).toInstant();
        }
        throw malFormada(expresion, "una fecha con hora (M-k/dd hh:mm o D-n hh:mm)");
    }

    /** Meses previstos de una partida: {@code M-3..M-2} → {@code jul-ago}; {@code M-3} → {@code jul}. */
    public String meses(String expresion) {
        Matcher m = coincidir(MESES_PREVISTOS, expresion, "meses previstos (M-k o M-k..M+j)");
        YearMonth desde = periodo(m.group(1));
        if (m.group(2) == null) {
            return abreviatura(desde);
        }
        YearMonth hasta = periodo(m.group(2));
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("En «" + expresion + "» el mes final es anterior al inicial");
        }
        return abreviatura(desde) + "-" + abreviatura(hasta);
    }

    private static String abreviatura(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1];
    }

    private static Matcher coincidir(Pattern patron, String expresion, String esperado) {
        Objects.requireNonNull(expresion, "expresion");
        Matcher m = patron.matcher(expresion);
        if (!m.matches()) {
            throw malFormada(expresion, esperado);
        }
        return m;
    }

    private static long desplazamiento(String grupo) {
        return grupo == null || grupo.equals("0") ? 0 : Long.parseLong(grupo);
    }

    private static LocalTime hora(String texto, String expresion) {
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La hora de «" + expresion + "» no es válida", e);
        }
    }

    private static IllegalArgumentException malFormada(String expresion, String esperado) {
        return new IllegalArgumentException("Expresión de fecha mal formada «" + expresion + "»: se esperaba "
                + esperado);
    }
}
