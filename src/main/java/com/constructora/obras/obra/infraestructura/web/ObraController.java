package com.constructora.obras.obra.infraestructura.web;

import com.constructora.obras.obra.infraestructura.web.dto.ObraResumenResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/obras")
public class ObraController {

    /**
     * Esqueleto que camina (fase 1, tarea 1.10): todavía no hay obras guardadas (la tabla llega con la 002), así que
     * la lista siempre está vacía. La 006 lo reemplaza por el caso de uso con filtros y visibilidad por usuario.
     * Roles: todos los de la plataforma (docs/contratos.md).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')")
    public List<ObraResumenResponse> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) String busqueda) {
        return List.of();
    }
}
