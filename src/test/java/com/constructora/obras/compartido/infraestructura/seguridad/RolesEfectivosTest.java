package com.constructora.obras.compartido.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RolesEfectivosTest {

    private static final List<String> DEMO1 = List.of("default-roles-supervision", "residente", "cliente");

    @Test
    void desdeElPortalSoloCuentaCliente() {
        assertThat(RolesEfectivos.de(DEMO1, "portal")).containsExactly("cliente");
        assertThat(RolesEfectivos.de(List.of("admin"), "portal")).isEmpty();
    }

    @Test
    void desdeElErpYLaAppSoloCuentanLosRolesInternos() {
        assertThat(RolesEfectivos.de(DEMO1, "erp")).containsExactly("residente");
        assertThat(RolesEfectivos.de(List.of("maestro", "almacenero", "cliente"), "app-obra"))
                .containsExactly("maestro", "almacenero");
    }

    @Test
    void losClientesDeServicioUsanLosRolesDelToken() {
        assertThat(RolesEfectivos.de(List.of("automatizacion"), "n8n")).containsExactly("automatizacion");
        assertThat(RolesEfectivos.de(List.of(), "gateway")).isEmpty();
    }
}
