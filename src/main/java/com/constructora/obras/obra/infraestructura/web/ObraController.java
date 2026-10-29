package com.constructora.obras.obra.infraestructura.web;

import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResponse;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResumenResponse;
import com.constructora.obras.obra.infraestructura.web.dto.RegistrarObraRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/obras")
public class ObraController {

    private final RegistrarObraCasoUso registrarObra;
    private final ObraWebMapper mapper;

    public ObraController(RegistrarObraCasoUso registrarObra, ObraWebMapper mapper) {
        this.registrarObra = registrarObra;
        this.mapper = mapper;
    }

    /**
     * 002 · Registrar obra. Roles: admin. Devuelve 201 con el detalle (estado SIN_CONTRATO) y publica ObraRegistrada.
     * El rol también se exige en ConfiguracionSeguridad para que un 403 llegue antes que la validación del cuerpo.
     */
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ObraResponse> registrar(@Valid @RequestBody RegistrarObraRequest pedido) {
        ObraDetalle detalle = registrarObra.ejecutar(mapper.aComando(pedido));
        return ResponseEntity.created(URI.create("/obras/" + detalle.obra().id()))
                .body(mapper.aRespuesta(detalle));
    }

    /**
     * Esqueleto que camina (fase 1, tarea 1.10): la lista siempre está vacía. La reemplaza la lista con visibilidad
     * por rol (002) y luego los filtros (006).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')")
    public List<ObraResumenResponse> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) String busqueda) {
        return List.of();
    }
}
