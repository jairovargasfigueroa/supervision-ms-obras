package com.constructora.obras.semilla;

import com.constructora.obras.compartido.dominio.Reloj;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Piezas del perfil semilla (011). Sin este perfil no existe nada de la semilla en la aplicación. */
@Configuration(proxyBeanMethods = false)
@Profile("semilla")
public class ConfiguracionSemilla {

    /**
     * La guarda va antes de las migraciones: con el perfil prod o una base que no es local, el proceso se detiene sin
     * haber tocado la base.
     */
    @Bean
    FlywayMigrationStrategy guardaAntesDeMigrar(Environment entorno, JdbcConnectionDetails conexion) {
        return flyway -> {
            GuardaSemilla.verificar(entorno.matchesProfiles("prod"), conexion.getJdbcUrl());
            flyway.migrate();
        };
    }

    @Bean
    CargaSemilla cargaSemilla(JdbcTemplate jdbc, TransactionTemplate transaccion) {
        return new CargaSemilla(jdbc, transaccion);
    }

    @Bean
    SemillaRunner semillaRunner(CargaSemilla carga, Environment entorno, JdbcConnectionDetails conexion, Reloj reloj,
            ConfigurableApplicationContext contexto) {
        return new SemillaRunner(carga, entorno, conexion, reloj, contexto);
    }
}
