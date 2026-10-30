package com.constructora.obras.semilla;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.ExitCodeGenerator;

/**
 * Guardas de la semilla (docs/datos-prueba.md, sección 12): se niega a cargar o vaciar si el perfil {@code prod} está
 * activo o si la base no es local ({@code localhost}, {@code 127.0.0.1}, {@code ::1} o el contenedor {@code postgres}
 * del Docker Compose de infra).
 */
public final class GuardaSemilla {

    /** Hosts aceptados: la máquina local y el nombre del servicio de PostgreSQL en infra/docker-compose.yml. */
    static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1", "::1", "postgres");
    private static final String PREFIJO = "jdbc:postgresql://";

    private GuardaSemilla() {
    }

    /**
     * @param perfilProd    el perfil prod está activo
     * @param urlDatasource URL JDBC de la base de Obras
     * @throws SemillaRechazadaException si no se puede correr
     */
    public static void verificar(boolean perfilProd, String urlDatasource) {
        if (perfilProd) {
            throw new SemillaRechazadaException(
                    "La semilla no corre con el perfil prod: los datos de prueba solo se cargan en desarrollo.");
        }
        Optional<String> host = host(urlDatasource);
        if (host.isEmpty() || !HOSTS_LOCALES.contains(host.get())) {
            throw new SemillaRechazadaException("La semilla solo corre contra una base local (localhost, 127.0.0.1 "
                    + "o el contenedor postgres de infra); la base configurada está en «"
                    + host.orElse("(no se pudo leer el host de la URL)") + "».");
        }
    }

    /** Host de una URL {@code jdbc:postgresql://host[:puerto]/base}; vacío si no tiene esa forma o trae varios hosts. */
    static Optional<String> host(String url) {
        if (url == null || !url.startsWith(PREFIJO)) {
            return Optional.empty();
        }
        String resto = url.substring(PREFIJO.length());
        int fin = indiceFin(resto);
        String autoridad = resto.substring(0, fin);
        if (autoridad.isEmpty() || autoridad.contains(",") || autoridad.contains("@")) {
            return Optional.empty();
        }
        String host;
        if (autoridad.startsWith("[")) {
            int cierre = autoridad.indexOf(']');
            if (cierre < 0) {
                return Optional.empty();
            }
            host = autoridad.substring(1, cierre);
        } else {
            int dosPuntos = autoridad.indexOf(':');
            host = dosPuntos < 0 ? autoridad : autoridad.substring(0, dosPuntos);
        }
        return host.isEmpty() ? Optional.empty() : Optional.of(host.toLowerCase(Locale.ROOT));
    }

    private static int indiceFin(String resto) {
        int fin = resto.length();
        for (char separador : new char[] {'/', '?'}) {
            int indice = resto.indexOf(separador);
            if (indice >= 0 && indice < fin) {
                fin = indice;
            }
        }
        return fin;
    }

    /**
     * La guarda rechazó la carga: el proceso termina con un mensaje claro y código de salida 2 (también si se lanza
     * al arrancar, antes de las migraciones: Spring Boot toma el código de la excepción).
     */
    public static final class SemillaRechazadaException extends RuntimeException implements ExitCodeGenerator {

        public static final int CODIGO_SALIDA = 2;

        public SemillaRechazadaException(String mensaje) {
            super(mensaje);
        }

        @Override
        public int getExitCode() {
            return CODIGO_SALIDA;
        }
    }
}
