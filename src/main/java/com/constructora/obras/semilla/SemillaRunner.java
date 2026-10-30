package com.constructora.obras.semilla;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.semilla.GuardaSemilla.SemillaRechazadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Perfil semilla (011): al terminar de arrancar (sin servidor web, sin consumidores ni publicador del outbox) carga la
 * parte de Obras de docs/datos-prueba.md con la fecha D de {@code semilla.fecha}, registra el resumen y cierra el
 * proceso. Códigos de salida: 0 bien, 1 error al cargar (no queda nada a medias), 2 la guarda rechazó la carga.
 */
public class SemillaRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(SemillaRunner.class);
    public static final int CODIGO_ERROR = 1;

    private final CargaSemilla carga;
    private final Environment entorno;
    private final JdbcConnectionDetails conexion;
    private final Reloj reloj;
    private final ConfigurableApplicationContext contexto;
    private volatile int codigoSalida;
    private volatile ResumenSemilla resumen;

    public SemillaRunner(CargaSemilla carga, Environment entorno, JdbcConnectionDetails conexion, Reloj reloj,
            ConfigurableApplicationContext contexto) {
        this.carga = carga;
        this.entorno = entorno;
        this.conexion = conexion;
        this.reloj = reloj;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            GuardaSemilla.verificar(entorno.matchesProfiles("prod"), conexion.getJdbcUrl());
            ModoSemilla modo = ModoSemilla.de(entorno.getProperty("semilla.modo", "cargar"));
            FechaRelativa fechas = FechaRelativa.desde(entorno.getProperty("semilla.fecha"), reloj);
            // Sin «D = …»: infra copia a su resumen solo la línea de cierre y RESUMEN_SEMILLA
            log.info("Semilla de Obras: empieza con la fecha {} en modo {}", fechas.d(), modo);
            resumen = carga.ejecutar(modo, fechas);
            resumen.lineas().forEach(log::info);
            codigoSalida = 0;
        } catch (SemillaRechazadaException error) {
            log.error("Semilla de Obras rechazada: {}", error.getMessage());
            codigoSalida = error.getExitCode();
        } catch (RuntimeException error) {
            log.error("La semilla de Obras falló y no cargó nada: {}", error.getMessage(), error);
            codigoSalida = CODIGO_ERROR;
        }
        if (entorno.getProperty("semilla.salir", Boolean.class, true)) {
            System.exit(SpringApplication.exit(contexto));
        }
    }

    @Override
    public int getExitCode() {
        return codigoSalida;
    }

    /** Resumen de la última carga (null si falló); lo leen las pruebas. */
    public ResumenSemilla resumen() {
        return resumen;
    }
}
