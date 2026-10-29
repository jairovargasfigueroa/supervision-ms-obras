package com.constructora.obras.planilla.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.infraestructura.mensajeria.ConfiguracionRabbit;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 008 · Endpoints del portal: aprobar (202, saga EN_CURSO y ReservarSaldo hacia la cola de Finanzas) y observar (200,
 * motivo de 10 a 500 caracteres), con sus errores, roles, la regla del azp y CORS para el portal. Obra con P-01 Losa
 * 10 m2 × 100 (contrato Bs 1.000, anticipo 10 %, retención 7 %).
 */
class RevisarPlanillaIT extends PruebaIntegracion {

    private static final String PERIODO = "2026-10";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin admin;
    @Autowired
    private JsonMapper json;

    private Equipo equipo;
    private UUID obra;
    private UUID losa;
    private Queue finanzas;

    @BeforeEach
    void obraYColaDeFinanzas() throws Exception {
        equipo = Equipo.nuevo();
        obra = ObrasPrueba.registrar(mvc, "Edificio Revisar", equipo);
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        finanzas = new Queue("prueba.finanzas." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(finanzas);
        admin.declareBinding(BindingBuilder.bind(finanzas).to(new TopicExchange(ConfiguracionRabbit.EXCHANGE))
                .with("comando.finanzas.#"));
    }

    @AfterEach
    void borrarCola() {
        admin.deleteQueue(finanzas.getName());
    }

    @Test
    void aprobarDeja202AprobandoLaSagaEnCursoYReservarSaldoEnLaColaDeFinanzas() throws Exception {
        UUID planilla = presentada(new BigDecimal("4"));

        aprobar(planilla, cliente())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("APROBANDO"));

        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.estado").value("APROBANDO"));
        Map<String, Object> saga = jdbc.queryForMap(
                "select id, paso_actual, estado, detalle::text as detalle, vence_en, actualizada_en"
                        + " from saga_aprobacion where planilla_id = ?", planilla);
        assertThat(saga.get("paso_actual")).isEqualTo("RESERVAR_SALDO");
        assertThat(saga.get("estado")).isEqualTo("EN_CURSO");
        assertThat(saga.get("detalle")).isEqualTo("{}");
        Instant creada = ((java.sql.Timestamp) saga.get("actualizada_en")).toInstant();
        assertThat(((java.sql.Timestamp) saga.get("vence_en")).toInstant()).isEqualTo(creada.plusSeconds(60));

        Message mensaje = recibir(finanzas, planilla);
        assertThat(mensaje.getMessageProperties().getReceivedRoutingKey()).isEqualTo("comando.finanzas.ReservarSaldo");
        String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        JsonNode sobre = json.readTree(cuerpo);
        assertThat(sobre.get("tipo").asString()).isEqualTo("ReservarSaldo");
        assertThat(sobre.get("origen").asString()).isEqualTo("obras");
        assertThat(sobre.get("correlacionId").asString()).isEqualTo(saga.get("id").toString());
        JsonNode datos = sobre.get("datos");
        assertThat(datos.propertyNames()).containsExactlyInAnyOrder("planillaId", "obraId", "monto");
        assertThat(datos.get("planillaId").asString()).isEqualTo(planilla.toString());
        assertThat(datos.get("obraId").asString()).isEqualTo(obra.toString());
        // 4 × 100 = 400 − 40 − 28 = 332,00, con 2 decimales en el texto
        assertThat(cuerpo).containsPattern("\"monto\":\\s*332\\.00\\b");
    }

    @Test
    void aprobarDosVecesOUnaQueNoEstaPresentadaEs409() throws Exception {
        UUID planilla = presentada(BigDecimal.ONE);
        aprobar(planilla, cliente()).andExpect(status().isAccepted());

        aprobar(planilla, cliente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_PRESENTADA"));
        observar(planilla, "Falta el respaldo de la losa", cliente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_PRESENTADA"));
        assertThat(jdbc.queryForObject("select count(*) from saga_aprobacion where planilla_id = ?", Integer.class,
                planilla)).isEqualTo(1);
    }

    @Test
    void observarLaDejaObservadaConElMotivoYPublicaPlanillaObservada() throws Exception {
        UUID planilla = presentada(BigDecimal.ONE);

        observar(planilla, "Falta el respaldo de la losa", cliente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OBSERVADA"))
                .andExpect(jsonPath("$.observacion").value("Falta el respaldo de la losa"))
                .andExpect(jsonPath("$.lineas[0].codigo").value("P-01"));

        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + residente()))
                .andExpect(jsonPath("$.estado").value("OBSERVADA"))
                .andExpect(jsonPath("$.observacion").value("Falta el respaldo de la losa"));
        JsonNode sobre = json.readTree(jdbc.queryForObject("""
                select payload::text from outbox_evento
                where clave = 'evento.obras.PlanillaObservada' and payload -> 'datos' ->> 'planillaId' = ?""",
                String.class, planilla.toString()));
        assertThat(sobre.get("correlacionId").asString()).isEqualTo(planilla.toString());
        assertThat(sobre.get("datos").get("obraId").asString()).isEqualTo(obra.toString());
        assertThat(sobre.get("datos").get("motivo").asString()).isEqualTo("Falta el respaldo de la losa");
        assertThat(sobre.get("datos").get("origen").asString()).isEqualTo("CLIENTE");
        assertThat(jdbc.queryForObject("select count(*) from saga_aprobacion where planilla_id = ?", Integer.class,
                planilla)).isZero();
    }

    @Test
    void elMotivoFueraDe10A500Es422YSinCuerpoEs400() throws Exception {
        UUID planilla = presentada(BigDecimal.ONE);

        observar(planilla, "corto", cliente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        observar(planilla, "x".repeat(501), cliente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
        mvc.perform(post("/planillas/{id}/observar", planilla).header("Authorization", "Bearer " + cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
        mvc.perform(post("/planillas/{id}/observar", planilla).header("Authorization", "Bearer " + cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.estado").value("PRESENTADA"));
    }

    @Test
    void soloElClienteDeLaObraDesdeElPortal() throws Exception {
        UUID planilla = presentada(BigDecimal.ONE);
        UUID borrador = PlanillasPrueba.insertar(jdbc, ObrasPrueba.registrar(mvc, "Otra", equipo), 1, PERIODO,
                "BORRADOR", Map.of());

        // Otros roles: 403 antes del cuerpo
        aprobar(planilla, residente()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(post("/planillas/{id}/observar", planilla).header("Authorization", "Bearer " + residente())
                        .contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isForbidden());
        aprobar(planilla, TokensPrueba.valido(ObrasPrueba.ADMIN, "admin")).andExpect(status().isForbidden());
        // Con azp = erp el rol cliente no cuenta (regla del azp)
        aprobar(planilla, TokensPrueba.desde("erp", equipo.cliente().toString(), "cliente"))
                .andExpect(status().isForbidden());
        // Cliente de otra obra o planilla que no existe: 404
        aprobar(planilla, TokensPrueba.desde("portal", UUID.randomUUID().toString(), "cliente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
        aprobar(UUID.randomUUID(), cliente()).andExpect(status().isNotFound());
        mvc.perform(post("/planillas/no-es-uuid/aprobar").header("Authorization", "Bearer " + cliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
        // Un borrador no existe para el cliente
        aprobar(borrador, cliente()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
        // Sin token: 401
        mvc.perform(post("/planillas/{id}/aprobar", planilla)).andExpect(status().isUnauthorized());

        mvc.perform(get("/planillas/{id}", planilla).header("Authorization", "Bearer " + cliente()))
                .andExpect(jsonPath("$.estado").value("PRESENTADA"));
    }

    @Test
    void elPortalPuedeLlamarAprobarYObservarDesdeElNavegador() throws Exception {
        for (String accion : List.of("aprobar", "observar")) {
            mvc.perform(options("/planillas/{id}/" + accion, UUID.randomUUID())
                            .header("Origin", "http://localhost:4300")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4300"));
        }
    }

    // --- ayudas ---

    /** Planilla del periodo PRESENTADA con esa cantidad de losa y sus totales (anticipo 10 %, retención 7 %). */
    private UUID presentada(BigDecimal cantidad) {
        UUID id = PlanillasPrueba.insertar(jdbc, obra, 1, PERIODO, "PRESENTADA", Map.of(losa, cantidad));
        BigDecimal subtotal = cantidad.multiply(BigDecimal.valueOf(100));
        BigDecimal anticipo = subtotal.multiply(new BigDecimal("0.10"));
        BigDecimal retencion = subtotal.multiply(new BigDecimal("0.07"));
        jdbc.update("update planilla set subtotal = ?, descuento_anticipo = ?, retencion = ?, neto = ? where id = ?",
                subtotal, anticipo, retencion, subtotal.subtract(anticipo).subtract(retencion), id);
        return id;
    }

    private String cliente() {
        return TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente");
    }

    private String residente() {
        return TokensPrueba.valido(equipo.residente().toString(), "residente");
    }

    private ResultActions aprobar(UUID planilla, String token) throws Exception {
        return mvc.perform(post("/planillas/{id}/aprobar", planilla).header("Authorization", "Bearer " + token));
    }

    private ResultActions observar(UUID planilla, String motivo, String token) throws Exception {
        return mvc.perform(post("/planillas/{id}/observar", planilla).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("motivo", motivo))));
    }

    private Message recibir(Queue cola, UUID planilla) {
        Instant fin = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive(cola.getName(), 500);
            if (mensaje != null && new String(mensaje.getBody(), StandardCharsets.UTF_8).contains(planilla.toString())) {
                return mensaje;
            }
        }
        throw new AssertionError("No llegó el comando de la planilla " + planilla);
    }
}
