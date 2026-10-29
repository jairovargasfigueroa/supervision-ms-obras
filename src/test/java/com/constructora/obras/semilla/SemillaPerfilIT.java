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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * RabbitMQ apunta a un puerto sin nada: la semilla no lo necesita. La primera prueba parte de la base vacía.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    @Order(1)
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
    @Order(2)
    void recargarVaciaTodoMenosElHistorialDeMigracionesYVuelveAlDocumentoConLaFechaDelDia() {
        try (ConfigurableApplicationContext contexto = semilla(List.of("semilla"), POSTGRES.getJdbcUrl())) {
            assertThat(contexto.getBean(SemillaRunner.class).getExitCode()).isZero();
        }
        long migraciones = contar("flyway_schema_history");
        assertThat(migraciones).isPositive();
        // Una demostración: se aprobó la planilla 3 de la obra 1 (con su saga y sus mensajes) y se registró otra obra
        String planilla3 = "d0000000-0000-4000-8000-000000000103";
        jdbc.update("update planilla set estado = 'APROBADA', cobro_numero = 'COB-2026-0004' where id = ?::uuid",
                planilla3);
        jdbc.update("""
                insert into saga_aprobacion (id, planilla_id, paso_actual, estado, detalle, vence_en, actualizada_en)
                values (gen_random_uuid(), ?::uuid, 'EMITIR_COBRO', 'COMPLETADA', '{}', now(), now())""", planilla3);
        jdbc.update("""
                insert into outbox_evento (id, tipo, clave, payload, creado_en)
                values (gen_random_uuid(), 'PlanillaAprobada', 'evento.obras.PlanillaAprobada', '{}', now())""");
        jdbc.update("insert into mensaje_procesado (id_mensaje, procesado_en) values (gen_random_uuid(), now())");
        jdbc.update("""
                insert into obra (id, nombre, ubicacion, estado, cliente_usuario_id, residente_usuario_id,
                                  maestro_usuario_id, almacenero_usuario_id, monto_contrato, plazo_dias, anticipo_pct,
                                  retencion_pct, fecha_inicio)
                select gen_random_uuid(), 'Obra de la demostración', 'Santa Cruz', 'SIN_CONTRATO',
                       cliente_usuario_id, residente_usuario_id, maestro_usuario_id, almacenero_usuario_id, 100, 30,
                       0, 0, current_date
                from obra limit 1""");

        try (ConfigurableApplicationContext contexto = semilla(List.of("semilla"), POSTGRES.getJdbcUrl(),
                "--semilla.modo=recargar", "--semilla.fecha=2026-11-05")) {
            SemillaRunner runner = contexto.getBean(SemillaRunner.class);
            assertThat(runner.getExitCode()).isZero();
            assertThat(runner.resumen().modo()).isEqualTo(ModoSemilla.RECARGAR);
            assertThat(runner.resumen().tablasVaciadas()).contains("obra", "planilla", "saga_aprobacion",
                    "outbox_evento", "mensaje_procesado").doesNotContain("flyway_schema_history");
            assertThat(runner.resumen().insertadas("obra")).isEqualTo(3);
        }
        assertThat(contar("flyway_schema_history")).isEqualTo(migraciones);
        assertThat(contar("obra")).isEqualTo(3);
        assertThat(contar("partida")).isEqualTo(21);
        assertThat(contar("planilla")).isEqualTo(5);
        assertThat(contar("foto_avance")).isEqualTo(10);
        assertThat(contar("saga_aprobacion")).isZero();
        assertThat(contar("outbox_evento")).isZero();
        assertThat(contar("mensaje_procesado")).isZero();
        assertThat(jdbc.queryForObject("select estado from planilla where id = ?::uuid", String.class, planilla3))
                .isEqualTo("PRESENTADA");
        // Las fechas se vuelven a calcular con la nueva D: obra 1 empieza en D-120 y la planilla 3 es de M-1
        assertThat(jdbc.queryForObject("select fecha_inicio::text from obra where id = ?::uuid", String.class,
                "b0000000-0000-4000-8000-000000000001")).isEqualTo("2026-07-08");
        assertThat(jdbc.queryForObject("select periodo::text from planilla where id = ?::uuid", String.class,
                planilla3)).isEqualTo("2026-10-01");
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
                "--semilla.salir=false"));
        argumentos.addAll(List.of(extra));
        // Un argumento repetido llegaría como «a,b»: la fecha fija va solo si la prueba no trae otra
        if (argumentos.stream().noneMatch(argumento -> argumento.startsWith("--semilla.fecha="))) {
            argumentos.add("--semilla.fecha=2026-10-28");
        }
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
