package com.constructora.obras.soporte;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints que solo existen en las pruebas, para probar la autorización por rol (001). */
@RestController
public class ControladorPruebaRoles {

    @GetMapping("/prueba/solo-admin-gerente")
    @PreAuthorize("hasAnyRole('admin','gerente')")
    public Map<String, String> soloAdminGerente() {
        return Map.of("ok", "si");
    }

    @GetMapping("/prueba/solo-cliente")
    @PreAuthorize("hasRole('cliente')")
    public Map<String, String> soloCliente() {
        return Map.of("ok", "si");
    }
}
