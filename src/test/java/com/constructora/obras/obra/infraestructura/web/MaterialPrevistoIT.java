package com.constructora.obras.obra.infraestructura.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 006 · GET /obras/{id}/material-previsto (criterio 5): planilla del periodo en cualquier estado × cantidad por
 * unidad; sin planilla, hayPlanilla false e items vacío. Roles: admin, gerente y residente asignado.
 */
class MaterialPrevistoIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private Equipo equipo;
    private UUID obra;
    private UUID losa;
    private UUID muro;

    /** Obra con P-01 Losa (cemento 1,1 y fierro 2,5 por m2) y P-02 Muro (ladrillo 30 por m2). */
    @BeforeEach
    void obraConDosPartidas() throws Exception {
        equipo = Equipo.nuevo();
        obra = ObrasPrueba.registrarDosPartidas(mvc, "Edificio Material", equipo);
        losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        muro = PlanillasPrueba.partida(jdbc, obra, "P-02");
    }

    @Test
    void conPlanillaEnElPeriodoMultiplicaLaCantidadPorElMaterialPorUnidad() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-09", "APROBADA", Map.of(losa, new BigDecimal("3"),
                muro, BigDecimal.ONE));
        PlanillasPrueba.insertar(jdbc, obra, 2, "2026-10", "BORRADOR", Map.of(losa, new BigDecimal("5"),
                muro, new BigDecimal("2.5")));

        pedir(obra, "2026-10", residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.hayPlanilla").value(true))
                .andExpect(jsonPath("$.items.length()").value(3))
                // Por código de partida y material: 5 × 1,1; 5 × 2,5; 2,5 × 30
                .andExpect(jsonPath("$.items[0].partidaId").value(losa.toString()))
                .andExpect(jsonPath("$.items[0].materialId").value("cemento"))
                .andExpect(jsonPath("$.items[0].cantidadPrevista").value(5.5))
                .andExpect(jsonPath("$.items[1].partidaId").value(losa.toString()))
                .andExpect(jsonPath("$.items[1].materialId").value("fierro"))
                .andExpect(jsonPath("$.items[1].cantidadPrevista").value(12.5))
                .andExpect(jsonPath("$.items[2].partidaId").value(muro.toString()))
                .andExpect(jsonPath("$.items[2].materialId").value("ladrillo"))
                .andExpect(jsonPath("$.items[2].cantidadPrevista").value(75));
        // Otro periodo usa solo su planilla
        pedir(obra, "2026-09", admin())
                .andExpect(jsonPath("$.hayPlanilla").value(true))
                .andExpect(jsonPath("$.items[0].cantidadPrevista").value(3.3))
                .andExpect(jsonPath("$.items[2].cantidadPrevista").value(30));
    }

    @Test
    void unaPartidaSinCantidadEnLaPlanillaTienePrevistoCero() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-10", "PRESENTADA", Map.of(losa, BigDecimal.ONE));

        pedir(obra, "2026-10", TokensPrueba.valido("u-gerente", "gerente"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].materialId").value("ladrillo"))
                .andExpect(jsonPath("$.items[2].cantidadPrevista").value(0));
    }

    @Test
    void sinPlanillaEnElPeriodoNoHayPrevisto() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-09", "APROBADA", Map.of(losa, BigDecimal.ONE));

        pedir(obra, "2026-10", residente())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        { "periodo": "2026-10", "hayPlanilla": false, "items": [] }""", true));
    }

    @Test
    void elRolSeCompruebaPrimero() throws Exception {
        esperarSinPermiso(pedir(obra, "2026-10", TokensPrueba.desde("app-obra", equipo.maestro().toString(),
                "maestro")));
        esperarSinPermiso(pedir(obra, "2026-10", TokensPrueba.desde("app-obra", equipo.almacenero().toString(),
                "almacenero")));
        esperarSinPermiso(pedir(obra, "2026-10", TokensPrueba.desde("portal", equipo.cliente().toString(),
                "cliente")));
        // Aunque el periodo esté mal escrito
        esperarSinPermiso(pedir(obra, "no", TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente")));
    }

    @Test
    void elResidenteQueNoEstaAsignadoRecibe404() throws Exception {
        esperarNoEncontrada(pedir(obra, "2026-10", TokensPrueba.valido(UUID.randomUUID().toString(), "residente")));
    }

    @Test
    void quienVeLaObraConOtroPapelRecibe403() throws Exception {
        // Tiene los roles residente y maestro, pero en esta obra es el maestro
        esperarSinPermiso(pedir(obra, "2026-10", TokensPrueba.valido(equipo.maestro().toString(), "residente",
                "maestro")));
    }

    @Test
    void conVariosRolesCuentanLosDeLaAplicacionDesdeLaQueEntro() throws Exception {
        // El residente de la obra que además es cliente: desde el ERP es residente; desde el portal, solo cliente
        String erp = TokensPrueba.desde("erp", equipo.residente().toString(), "residente", "cliente");
        String portal = TokensPrueba.desde("portal", equipo.residente().toString(), "residente", "cliente");

        pedir(obra, "2026-10", erp).andExpect(status().isOk());
        esperarSinPermiso(pedir(obra, "2026-10", portal));
    }

    @Test
    void obraInexistenteOIdInvalidoResponde404() throws Exception {
        esperarNoEncontrada(pedir(UUID.randomUUID(), "2026-10", admin()));
        esperarNoEncontrada(mvc.perform(get("/obras/no-es-un-uuid/material-previsto").param("periodo", "2026-10")
                .header("Authorization", "Bearer " + admin())));
    }

    @Test
    void periodoQueFaltaOMalEscritoResponde400() throws Exception {
        for (String periodo : new String[] {null, "", "2026-13", "2026-1", "10-2026", "2026-10-01"}) {
            pedir(obra, periodo, admin())
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("periodo"));
        }
    }

    private String residente() {
        return TokensPrueba.valido(equipo.residente().toString(), "residente");
    }

    private static String admin() {
        return TokensPrueba.valido(ObrasPrueba.ADMIN, "admin");
    }

    private ResultActions pedir(UUID obraId, String periodo, String token) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/obras/" + obraId + "/material-previsto")
                .header("Authorization", "Bearer " + token);
        if (periodo != null) {
            pedido.param("periodo", periodo);
        }
        return mvc.perform(pedido);
    }

    private static void esperarSinPermiso(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    private static void esperarNoEncontrada(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
    }
}
