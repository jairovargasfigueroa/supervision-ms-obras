package com.constructora.obras.compartido.infraestructura.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 001, criterio 9: sin token 401; con token válido sin el rol 403 SIN_PERMISO; con el rol 200. */
class RolesIT extends PruebaIntegracion {

    private static final String SOLO_ADMIN_GERENTE = "/prueba/solo-admin-gerente";
    private static final String SOLO_CLIENTE = "/prueba/solo-cliente";
    private static final String DEMO1 = "a0000000-0000-4000-8000-000000000009";

    @Autowired
    private MockMvc mvc;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get(SOLO_ADMIN_GERENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void conOtroRolResponde403SinPermiso() throws Exception {
        esperarSinPermiso(pedir(SOLO_ADMIN_GERENTE, TokensPrueba.valido("u-residente", "residente")));
    }

    @Test
    void conElRolResponde200() throws Exception {
        pedir(SOLO_ADMIN_GERENTE, TokensPrueba.valido("u-admin", "admin")).andExpect(status().isOk());
        pedir(SOLO_ADMIN_GERENTE, TokensPrueba.valido("u-gerente", "gerente")).andExpect(status().isOk());
    }

    @Test
    void demo1EsClienteSoloDesdeElPortal() throws Exception {
        pedir(SOLO_CLIENTE, TokensPrueba.desde("portal", DEMO1, "residente", "cliente")).andExpect(status().isOk());
        esperarSinPermiso(pedir(SOLO_CLIENTE, TokensPrueba.desde("erp", DEMO1, "residente", "cliente")));
    }

    @Test
    void desdeElPortalNoCuentanLosRolesInternos() throws Exception {
        esperarSinPermiso(pedir(SOLO_ADMIN_GERENTE, TokensPrueba.desde("portal", "u-admin", "admin", "cliente")));
    }

    @Test
    void obrasAceptaTodosLosRolesDeLaPlataforma() throws Exception {
        pedir("/obras", TokensPrueba.desde("app-obra", "u-maestro", "maestro")).andExpect(status().isOk());
        pedir("/obras", TokensPrueba.desde("portal", "u-cliente", "cliente")).andExpect(status().isOk());
        esperarSinPermiso(pedir("/obras", TokensPrueba.valido("u-sin-rol", "default-roles-supervision")));
    }

    private ResultActions pedir(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private static void esperarSinPermiso(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }
}
