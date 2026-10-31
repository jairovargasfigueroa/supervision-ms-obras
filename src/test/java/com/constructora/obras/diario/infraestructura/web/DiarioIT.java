package com.constructora.obras.diario.infraestructura.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 103 · POST y GET /obras/{obraId}/diario de punta a punta (con el reloj real: hoy en La Paz). */
class DiarioIT extends PruebaIntegracion {

    private static final String TEXTO = "Se vació la losa del 2do piso. Lluvia por la tarde, se cubrió con nylon.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoy = LocalDate.now(Reloj.ZONA_NEGOCIO);
    private Equipo equipo;
    private UUID obraId;
    private String maestro;

    @BeforeEach
    void obraEnEjecucion() throws Exception {
        equipo = Equipo.nuevo();
        obraId = ObrasPrueba.registrar(mvc, "Obra con diario", equipo);
        PlanillasPrueba.cambiarEstado(jdbc, obraId, "EN_EJECUCION");
        PlanillasPrueba.cambiarInicio(jdbc, obraId, hoy.minusDays(60));
        maestro = TokensPrueba.desde("app-obra", equipo.maestro().toString(), "maestro");
    }

    @Test
    void elMaestroRegistraYLaListaLaDevuelve() throws Exception {
        UUID id = UUID.randomUUID();

        registrar(obraId, cuerpo(id, hoy, "LLUVIA_LEVE", TEXTO), maestro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.obraId").value(obraId.toString()))
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.clima").value("LLUVIA_LEVE"))
                .andExpect(jsonPath("$.texto").value(TEXTO))
                .andExpect(jsonPath("$.registradoPor").value(equipo.maestro().toString()))
                .andExpect(jsonPath("$.registradoEn").isString());

        listar(obraId, "", maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    ResultActions registrar(UUID obra, String cuerpo, String token) throws Exception {
        return mvc.perform(post("/obras/" + obra + "/diario").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    ResultActions listar(UUID obra, String consulta, String token) throws Exception {
        return mvc.perform(get("/obras/" + obra + "/diario" + consulta).header("Authorization", "Bearer " + token));
    }

    static String cuerpo(UUID id, LocalDate fecha, String clima, String texto) {
        return """
                { "id": "%s", "fecha": "%s", "clima": "%s", "texto": "%s" }""".formatted(id, fecha, clima, texto);
    }
}
