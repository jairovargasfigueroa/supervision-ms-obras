package com.constructora.obras.obra.infraestructura.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Tarea 1.10: GET /obras responde con token (un residente sin obras asignadas recibe la lista vacía) y exige token. */
class ObraControllerIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

    @Test
    void listaVaciaConToken() throws Exception {
        mvc.perform(get("/obras").param("estado", "EN_EJECUCION")
                .header("Authorization", "Bearer " + TokensPrueba.valido(java.util.UUID.randomUUID().toString(),
                        "residente")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/obras"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
