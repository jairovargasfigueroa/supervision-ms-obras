package com.constructora.obras.obra.infraestructura.web;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResponse;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResumenResponse;
import com.constructora.obras.obra.infraestructura.web.dto.RegistrarObraRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/obras")
public class ObraController {

    private static final String TODOS_LOS_ROLES =
            "hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')";

    private final RegistrarObraCasoUso registrarObra;
    private final ConsultarObraCasoUso consultarObra;
    private final UsuarioActual usuarioActual;
    private final ObraWebMapper mapper;

    public ObraController(RegistrarObraCasoUso registrarObra, ConsultarObraCasoUso consultarObra,
            UsuarioActual usuarioActual, ObraWebMapper mapper) {
        this.registrarObra = registrarObra;
        this.consultarObra = consultarObra;
        this.usuarioActual = usuarioActual;
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
     * Obras que el usuario puede ver (002, criterio 7: la obra recién registrada aparece en SIN_CONTRATO).
     * Los filtros estado y busqueda son de la 006: por ahora se aceptan y no filtran.
     */
    @GetMapping
    @PreAuthorize(TODOS_LOS_ROLES)
    public List<ObraResumenResponse> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) String busqueda) {
        return consultarObra.listar(usuarioActual.obtener()).stream().map(mapper::aResumen).toList();
    }

    /** Detalle (002, 006). Si no existe, no la puede ver o el id no es un UUID: 404 OBRA_NO_ENCONTRADA. */
    @GetMapping("/{id}")
    @PreAuthorize(TODOS_LOS_ROLES)
    public ObraResponse obtener(@PathVariable String id) {
        return mapper.aRespuesta(consultarObra.obtener(uuid(id), usuarioActual.obtener()));
    }

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "Obra no encontrada");
        }
    }
}
