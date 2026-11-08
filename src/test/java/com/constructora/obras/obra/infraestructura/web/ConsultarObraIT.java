package com.constructora.obras.obra.infraestructura.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tarea 6: GET /obras/{id} y GET /obras con la visibilidad por rol (convenciones, «Autenticación y permisos»). */
class ConsultarObraIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Reloj reloj;

    private Equipo equipo;
    private UUID obraId;

    @BeforeEach
    void registrarObra() throws Exception {
        equipo = Equipo.nuevo();
        obraId = ObrasPrueba.registrar(mvc, "Condominio Visible", equipo);
    }

    @Test
    void detalleConEstadoYMotivoDeRechazo() throws Exception {
        pedir("/obras/" + obraId, TokensPrueba.valido(ObrasPrueba.ADMIN, "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(obraId.toString()))
                .andExpect(jsonPath("$.estado").value("SIN_CONTRATO"))
                .andExpect(jsonPath("$.motivoRechazoContrato").isEmpty())
                .andExpect(jsonPath("$.contrato.monto").value(1000))
                .andExpect(jsonPath("$.contrato.anticipoPct").value(10))
                .andExpect(jsonPath("$.partidas[0].codigo").value("P-01"))
                .andExpect(jsonPath("$.partidas[0].insumos[0].cantidadPorUnidad").value(1.1));
    }

    @Test
    void adminYGerenteVenCualquierObra() throws Exception {
        pedir("/obras/" + obraId, TokensPrueba.valido("u-gerente", "gerente")).andExpect(status().isOk());
        pedir("/obras", TokensPrueba.valido("u-gerente", "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(obraId.toString())));
    }

    @Test
    void cadaAsignadoVeLaObraDesdeSuAplicacion() throws Exception {
        pedir("/obras/" + obraId, TokensPrueba.valido(equipo.residente().toString(), "residente"))
                .andExpect(status().isOk());
        pedir("/obras/" + obraId, TokensPrueba.desde("app-obra", equipo.maestro().toString(), "maestro"))
                .andExpect(status().isOk());
        pedir("/obras/" + obraId, TokensPrueba.desde("app-obra", equipo.almacenero().toString(), "almacenero"))
                .andExpect(status().isOk());
        pedir("/obras/" + obraId, TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                .andExpect(status().isOk());
    }

    @Test
    void quienNoEstaAsignadoRecibe404ComoSiNoExistiera() throws Exception {
        esperarNoEncontrada(pedir("/obras/" + obraId, TokensPrueba.valido(UUID.randomUUID().toString(), "residente")));
        // El maestro de la obra no la ve como almacenero, ni el residente como cliente
        esperarNoEncontrada(pedir("/obras/" + obraId,
                TokensPrueba.desde("app-obra", equipo.maestro().toString(), "almacenero")));
        // Usuario con varios roles: desde el ERP solo cuenta residente, y es cliente (no residente) de esta obra
        esperarNoEncontrada(pedir("/obras/" + obraId,
                TokensPrueba.desde("erp", equipo.cliente().toString(), "residente", "cliente")));
    }

    @Test
    void obraInexistenteOIdInvalidoResponde404() throws Exception {
        String admin = TokensPrueba.valido(ObrasPrueba.ADMIN, "admin");
        esperarNoEncontrada(pedir("/obras/" + UUID.randomUUID(), admin));
        esperarNoEncontrada(pedir("/obras/no-es-un-uuid", admin));
    }

    @Test
    void laListaMuestraLaObraRecienRegistradaSinContrato() throws Exception {
        UUID otraObra = ObrasPrueba.registrar(mvc, "Otra obra", Equipo.nuevo());

        pedir("/obras", TokensPrueba.valido(equipo.residente().toString(), "residente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(obraId.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Condominio Visible"))
                .andExpect(jsonPath("$[0].estado").value("SIN_CONTRATO"))
                .andExpect(jsonPath("$[0].fechaInicio").value("2026-11-01"))
                .andExpect(jsonPath("$[0].avancePct").value(0))
                .andExpect(jsonPath("$[0].plazoPct").isNumber())
                .andExpect(jsonPath("$[0].enRiesgo").isBoolean())
                .andExpect(jsonPath("$[0].ultimaPlanilla").isEmpty());
        pedir("/obras", TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                .andExpect(jsonPath("$[*].id", hasItem(obraId.toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(otraObra.toString()))));
        pedir("/obras", TokensPrueba.desde("app-obra", equipo.almacenero().toString(), "almacenero"))
                .andExpect(jsonPath("$.length()").value(1));
        pedir("/obras", TokensPrueba.valido(UUID.randomUUID().toString(), "residente"))
                .andExpect(content().json("[]"));
    }

    @Nested
    class Filtros {

        private Equipo mismoEquipo;
        private UUID torre;
        private UUID casa;
        private String residente;

        @BeforeEach
        void dosObrasDelMismoResidente() throws Exception {
            mismoEquipo = Equipo.nuevo();
            torre = ObrasPrueba.registrar(mvc, "Torre Norte", mismoEquipo);
            casa = ObrasPrueba.registrar(mvc, "Casa Sur 50%_A", mismoEquipo);
            PlanillasPrueba.cambiarEstado(jdbc, casa, "EN_EJECUCION");
            residente = TokensPrueba.valido(mismoEquipo.residente().toString(), "residente");
        }

        @Test
        void sinFiltrosVeTodasLasSuyasPorNombre() throws Exception {
            pedir("/obras", residente)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(casa.toString()))
                    .andExpect(jsonPath("$[1].id").value(torre.toString()));
            listar(residente, "estado", "", "busqueda", "  ").andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void filtraPorEstado() throws Exception {
            listar(residente, "estado", "EN_EJECUCION")
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(casa.toString()))
                    .andExpect(jsonPath("$[0].estado").value("EN_EJECUCION"));
            listar(residente, "estado", "SIN_CONTRATO")
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(torre.toString()));
            listar(residente, "estado", "CONCLUIDA").andExpect(content().json("[]"));
        }

        @Test
        void buscaPorNombreSinDistinguirMayusculas() throws Exception {
            listar(residente, "busqueda", "torre")
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(torre.toString()));
            listar(residente, "busqueda", " NORTE ")
                    .andExpect(jsonPath("$[*].id", contains(torre.toString())));
            listar(residente, "busqueda", "Santa").andExpect(content().json("[]"));
        }

        @Test
        void losComodinesDeLaBusquedaSonTextoLiteral() throws Exception {
            listar(residente, "busqueda", "50%_a")
                    .andExpect(jsonPath("$[*].id", contains(casa.toString())));
            listar(residente, "busqueda", "%")
                    .andExpect(jsonPath("$[*].id", contains(casa.toString())));
            listar(residente, "busqueda", "_")
                    .andExpect(jsonPath("$[*].id", contains(casa.toString())));
        }

        @Test
        void estadoYBusquedaSeCombinanYRespetanLaVisibilidad() throws Exception {
            listar(residente, "estado", "EN_EJECUCION", "busqueda", "torre").andExpect(content().json("[]"));
            // Otro residente no ve estas obras aunque busque su nombre
            listar(TokensPrueba.valido(UUID.randomUUID().toString(), "residente"), "busqueda", "Torre Norte")
                    .andExpect(content().json("[]"));
            // El gerente las ve todas
            listar(TokensPrueba.valido("u-gerente", "gerente"), "busqueda", "Torre Norte")
                    .andExpect(jsonPath("$[*].id", hasItem(torre.toString())))
                    .andExpect(jsonPath("$[*].id", not(hasItem(casa.toString()))));
        }

        @Test
        void unEstadoQueNoExisteResponde400() throws Exception {
            listar(residente, "estado", "ACTIVA")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        }
    }

    @Nested
    class IndicadoresDeLaLista {

        @Test
        void soloLasPlanillasAprobadasCuentanParaElAvanceYLaUltimaEsLaDePeriodoMasReciente() throws Exception {
            UUID partida = PlanillasPrueba.partida(jdbc, obraId, "P-01");
            // Plazo de 100 días: iniciada hace 50 → plazo 50 %
            PlanillasPrueba.cambiarInicio(jdbc, obraId, reloj.hoy().minusDays(50));
            PlanillasPrueba.insertar(jdbc, obraId, 1, "2026-08", "APROBADA", Map.of(partida, new BigDecimal("2")));
            PlanillasPrueba.insertar(jdbc, obraId, 2, "2026-09", "APROBADA", Map.of(partida, BigDecimal.ONE));
            PlanillasPrueba.insertar(jdbc, obraId, 3, "2026-10", "PRESENTADA", Map.of(partida, new BigDecimal("5")));

            // (2 + 1) × 100 / 1000 = 30 %; la PRESENTADA no cuenta; 50 − 30 = 20 puntos: en riesgo
            pedir("/obras", TokensPrueba.valido(equipo.residente().toString(), "residente"))
                    .andExpect(jsonPath("$[0].id").value(obraId.toString()))
                    .andExpect(jsonPath("$[0].avancePct").value(30.0))
                    .andExpect(jsonPath("$[0].plazoPct").value(50.0))
                    .andExpect(jsonPath("$[0].enRiesgo").value(true))
                    .andExpect(jsonPath("$[0].ultimaPlanilla.periodo").value("2026-10"))
                    .andExpect(jsonPath("$[0].ultimaPlanilla.estado").value("PRESENTADA"));
        }

        @Test
        void sinRiesgoCuandoElAvanceVaAlDiaYLaUltimaPuedeSerAprobada() throws Exception {
            UUID partida = PlanillasPrueba.partida(jdbc, obraId, "P-01");
            PlanillasPrueba.cambiarInicio(jdbc, obraId, reloj.hoy().minusDays(40));
            PlanillasPrueba.insertar(jdbc, obraId, 1, "2026-09", "APROBADA", Map.of(partida, new BigDecimal("3")));

            // plazo 40 %, avance 30 %: 10 puntos justos, no está en riesgo
            pedir("/obras", TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                    .andExpect(jsonPath("$[0].avancePct").value(30.0))
                    .andExpect(jsonPath("$[0].plazoPct").value(40.0))
                    .andExpect(jsonPath("$[0].enRiesgo").value(false))
                    .andExpect(jsonPath("$[0].ultimaPlanilla.periodo").value("2026-09"))
                    .andExpect(jsonPath("$[0].ultimaPlanilla.estado").value("APROBADA"));
        }

        @Test
        void lasPlanillasDeOtraObraNoCuentan() throws Exception {
            UUID otra = ObrasPrueba.registrar(mvc, "Obra ajena", Equipo.nuevo());
            PlanillasPrueba.insertar(jdbc, otra, 1, "2026-09", "APROBADA",
                    Map.of(PlanillasPrueba.partida(jdbc, otra, "P-01"), BigDecimal.TEN));

            pedir("/obras", TokensPrueba.valido(equipo.residente().toString(), "residente"))
                    .andExpect(jsonPath("$[0].avancePct").value(0))
                    .andExpect(jsonPath("$[0].ultimaPlanilla").isEmpty());
        }
    }

    /** 006, criterio 3: tabla de partidas con ejecutada acumulada y avance; indicadores de la obra. */
    @Nested
    class DetalleConAvance {

        private Equipo equipoDetalle;
        private UUID obra;
        private UUID losa;
        private UUID muro;

        @BeforeEach
        void obraConDosPartidas() throws Exception {
            equipoDetalle = Equipo.nuevo();
            obra = ObrasPrueba.registrarDosPartidas(mvc, "Edificio Detalle", equipoDetalle);
            losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
            muro = PlanillasPrueba.partida(jdbc, obra, "P-02");
        }

        @Test
        void partidasConEjecutadaAcumuladaYAvanceDeLasPlanillasAprobadas() throws Exception {
            PlanillasPrueba.cambiarInicio(jdbc, obra, reloj.hoy().minusDays(30));
            PlanillasPrueba.insertar(jdbc, obra, 1, "2026-07", "APROBADA",
                    Map.of(losa, new BigDecimal("2"), muro, new BigDecimal("4")));
            PlanillasPrueba.insertar(jdbc, obra, 2, "2026-08", "APROBADA",
                    Map.of(losa, new BigDecimal("1.5"), muro, BigDecimal.ZERO));
            PlanillasPrueba.insertar(jdbc, obra, 3, "2026-09", "APROBANDO",
                    Map.of(losa, new BigDecimal("5"), muro, new BigDecimal("5")));

            // Losa 3,5 de 10 (35 %), muro 4 de 20 (20 %); obra (3,5 × 100 + 4 × 50) / 2000 = 27,5 %; plazo 30 %
            pedir("/obras/" + obra, TokensPrueba.valido(equipoDetalle.residente().toString(), "residente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avancePct").value(27.5))
                    .andExpect(jsonPath("$.plazoPct").value(30.0))
                    .andExpect(jsonPath("$.enRiesgo").value(false))
                    .andExpect(jsonPath("$.partidas.length()").value(2))
                    .andExpect(jsonPath("$.partidas[0].codigo").value("P-01"))
                    .andExpect(jsonPath("$.partidas[0].unidad").value("m2"))
                    .andExpect(jsonPath("$.partidas[0].cantidadContratada").value(10))
                    .andExpect(jsonPath("$.partidas[0].precioUnitario").value(100))
                    .andExpect(jsonPath("$.partidas[0].ejecutadaAcumulada").value(3.5))
                    .andExpect(jsonPath("$.partidas[0].avancePct").value(35.0))
                    .andExpect(jsonPath("$.partidas[1].codigo").value("P-02"))
                    .andExpect(jsonPath("$.partidas[1].ejecutadaAcumulada").value(4))
                    .andExpect(jsonPath("$.partidas[1].avancePct").value(20.0))
                    .andExpect(jsonPath("$.contrato.monto").value(2000))
                    .andExpect(jsonPath("$.contrato.plazoDias").value(100))
                    .andExpect(jsonPath("$.asignaciones.residenteId").value(equipoDetalle.residente().toString()));
        }

        @Test
        void lasPlanillasQueNoEstanAprobadasNoCuentan() throws Exception {
            String[] estados = {"BORRADOR", "PRESENTADA", "APROBANDO", "OBSERVADA"};
            for (int i = 0; i < estados.length; i++) {
                PlanillasPrueba.insertar(jdbc, obra, i + 1, "2026-0" + (i + 5), estados[i],
                        Map.of(losa, BigDecimal.ONE, muro, BigDecimal.ONE));
            }

            pedir("/obras/" + obra, TokensPrueba.valido(ObrasPrueba.ADMIN, "admin"))
                    .andExpect(jsonPath("$.avancePct").value(0))
                    .andExpect(jsonPath("$.partidas[0].ejecutadaAcumulada").value(0))
                    .andExpect(jsonPath("$.partidas[0].avancePct").value(0))
                    .andExpect(jsonPath("$.partidas[1].ejecutadaAcumulada").value(0));
        }

        @Test
        void enRiesgoCuandoElPlazoSuperaAlAvancePorMasDeDiezPuntos() throws Exception {
            // Iniciada hace 120 días con plazo de 100: plazo 100 %; avance 27,5 %
            PlanillasPrueba.cambiarInicio(jdbc, obra, reloj.hoy().minusDays(120));
            PlanillasPrueba.insertar(jdbc, obra, 1, "2026-07", "APROBADA",
                    Map.of(losa, new BigDecimal("3.5"), muro, new BigDecimal("4")));

            pedir("/obras/" + obra, TokensPrueba.valido("u-gerente", "gerente"))
                    .andExpect(jsonPath("$.avancePct").value(27.5))
                    .andExpect(jsonPath("$.plazoPct").value(100.0))
                    .andExpect(jsonPath("$.enRiesgo").value(true));
        }

        @Test
        void antesDeIniciarElPlazoEsCero() throws Exception {
            PlanillasPrueba.cambiarInicio(jdbc, obra, reloj.hoy().plusDays(10));

            pedir("/obras/" + obra, TokensPrueba.valido(ObrasPrueba.ADMIN, "admin"))
                    .andExpect(jsonPath("$.plazoPct").value(0))
                    .andExpect(jsonPath("$.enRiesgo").value(false));
        }
    }

    /** GET /obras con parámetros (nombre, valor, …) enviados tal cual, sin codificar a mano. */
    private ResultActions listar(String token, String... parametros) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/obras").header("Authorization", "Bearer " + token);
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private static void esperarNoEncontrada(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
    }
}
