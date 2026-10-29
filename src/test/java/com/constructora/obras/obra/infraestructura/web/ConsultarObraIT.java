package com.constructora.obras.obra.infraestructura.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tarea 6: GET /obras/{id} y GET /obras con la visibilidad por rol (convenciones, «Autenticación y permisos»). */
class ConsultarObraIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

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

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private static void esperarNoEncontrada(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
    }
}
