package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.ObrasApplication;
import com.constructora.obras.compartido.infraestructura.mensajeria.PublicadorOutbox;
import com.constructora.obras.compartido.infraestructura.mensajeria.ReceptorMensajes;
import com.constructora.obras.semilla.GuardaSemilla.SemillaRechazadaException;
import com.constructora.obras.saga.infraestructura.programacion.VencimientoSagaTarea;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 011 · La aplicación con el perfil semilla, como la arranca infra: sin servidor web, sin consumidores ni publicador
 * del outbox, carga con D fija y termina (aquí con semilla.salir = false para no cerrar la JVM de las pruebas).
 * RabbitMQ apunta a un puerto sin nada: la semilla no lo necesita.
 */
class SemillaPerfilIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void iniciar() {
        POSTGRES.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }

    @AfterAll
    static void detener() {
        POSTGRES.stop();
    }

    @Test
    void cargaSinWebNiMensajeriaYNoDuplicaAlRepetir() {
        try (ConfigurableApplicationContext contexto = semilla(List.of("semilla"), POSTGRES.getJdbcUrl())) {
            assertThat(contexto.getBeanNamesForType(PublicadorOutbox.class)).isEmpty();
            assertThat(contexto.getBeanNamesForType(VencimientoSagaTarea.class)).isEmpty();
            assertThat(contexto.getBean(ReceptorMensajes.class)).isNotNull();
            assertThat(contexto.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers())
                    .allMatch(contenedor -> !contenedor.isRunning());
            SemillaRunner runner = contexto.getBean(SemillaRunner.class);
            assertThat(SpringApplication.exit(contexto)).isZero();
            assertThat(runner.resumen().d()).hasToString("2026-10-28");
            assertThat(runner.resumen().insertadas("obra")).isEqualTo(3);
        }
        assertThat(contar("obra")).isEqualTo(3);
        assertThat(contar("partida")).isEqualTo(21);
        assertThat(contar("planilla")).isEqualTo(5);
        assertThat(contar("foto_avance")).isEqualTo(10);
        assertThat(contar("outbox_evento")).isZero();
        assertThat(contar("mensaje_procesado")).isZero();
        assertThat(contar("saga_aprobacion")).isZero();

        try (ConfigurableApplicationContext contexto = semilla(List.of("semilla"), POSTGRES.getJdbcUrl())) {
            SemillaRunner runner = contexto.getBean(SemillaRunner.class);
            assertThat(runner.getExitCode()).isZero();
            CargaSemilla.TABLAS.forEach(tabla -> assertThat(runner.resumen().insertadas(tabla)).isZero());
            assertThat(runner.resumen().total("obra")).isEqualTo(3);
            assertThat(runner.resumen().lineaInfra()).isEqualTo("SEMILLA_RESUMEN servicio=obras fecha=2026-10-28 "
                    + "modo=cargar obras=3 partidas=21 insumos=58 fotos=10 planillas=5 lineas=34 fotosAdjuntas=5");
        }
        assertThat(contar("obra")).isEqualTo(3);
        assertThat(contar("linea_planilla")).isEqualTo(34);
    }

    @Test
    void conElPerfilProdSeNiegaAntesDeMigrar() {
        assertThatThrownBy(() -> semilla(List.of("semilla", "prod"), POSTGRES.getJdbcUrl()).close())
                .hasRootCauseInstanceOf(SemillaRechazadaException.class)
                .rootCause().hasMessageContaining("prod");
    }

    @Test
    void conUnaBaseQueNoEsLocalSeNiegaSinConectarse() {
        assertThatThrownBy(() -> semilla(List.of("semilla"),
                "jdbc:postgresql://obras.abc123.sa-east-1.rds.amazonaws.com:5432/obras_db").close())
                .hasRootCauseInstanceOf(SemillaRechazadaException.class)
                .rootCause().hasMessageContaining("base local");
    }

    @Test
    void unModoDesconocidoTerminaConError() {
        try (ConfigurableApplicationContext contexto = semilla(List.of("semilla"), POSTGRES.getJdbcUrl(),
                "--semilla.modo=borrar")) {
            assertThat(contexto.getBean(SemillaRunner.class).getExitCode()).isEqualTo(SemillaRunner.CODIGO_ERROR);
        }
    }

    private static ConfigurableApplicationContext semilla(List<String> perfiles, String url, String... extra) {
        List<String> argumentos = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.rabbitmq.port=1",
                "--semilla.fecha=2026-10-28",
                "--semilla.salir=false"));
        argumentos.addAll(List.of(extra));
        SpringApplicationBuilder aplicacion = new SpringApplicationBuilder(ObrasApplication.class)
                .profiles(perfiles.toArray(String[]::new));
        ConfigurableApplicationContext contexto = aplicacion.run(argumentos.toArray(String[]::new));
        assertThat(aplicacion.application().getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
        return contexto;
    }

    private static long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }
}
