package com.constructora.obras.compartido.infraestructura.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /salud sin token (convenciones, «Registros y salud»). */
@RestController
public class SaludController {

    @GetMapping("/salud")
    public Map<String, String> salud() {
        return Map.of("estado", "ok");
    }
}
