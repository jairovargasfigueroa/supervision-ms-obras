package com.constructora.obras.obra.infraestructura.web;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.infraestructura.web.dto.MaterialPrevistoResponse;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResponse;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResumenResponse;
import com.constructora.obras.obra.infraestructura.web.dto.RegistrarObraRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    private static final String TODOS_LOS_ROLES =
            "hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')";

    private final RegistrarObraCasoUso registrarObra;
    private final ConsultarObraCasoUso consultarObra;
    private final ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto;
    private final UsuarioActual usuarioActual;
    private final ObraWebMapper mapper;

    public ObraController(RegistrarObraCasoUso registrarObra, ConsultarObraCasoUso consultarObra,
            ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto, UsuarioActual usuarioActual,
            ObraWebMapper mapper) {
        this.registrarObra = registrarObra;
        this.consultarObra = consultarObra;
        this.consultarMaterialPrevisto = consultarMaterialPrevisto;
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
     * Obras que el usuario puede ver, por nombre, con avance, plazo, riesgo y última planilla (002, 006). Filtros
     * (006, criterio 2): estado exacto y búsqueda por nombre; vacíos no filtran. Un estado que no existe: 400
     * DATOS_INVALIDOS con errores[].campo = estado.
     */
    @GetMapping
    @PreAuthorize(TODOS_LOS_ROLES)
    public List<ObraResumenResponse> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) String busqueda) {
        FiltroObras filtro = new FiltroObras(estado(estado), busqueda);
        return consultarObra.listar(usuarioActual.obtener(), filtro).stream().map(mapper::aResumen).toList();
    }

    /** Detalle (002, 006). Si no existe, no la puede ver o el id no es un UUID: 404 OBRA_NO_ENCONTRADA. */
    @GetMapping("/{id}")
    @PreAuthorize(TODOS_LOS_ROLES)
    public ObraResponse obtener(@PathVariable String id) {
        return mapper.aRespuesta(consultarObra.obtener(uuid(id), usuarioActual.obtener()));
    }

    /**
     * 006 · Material previsto del periodo (AAAA-MM). Roles: admin, gerente y residente asignado. Orden: rol (403) →
     * periodo (400 DATOS_INVALIDOS si falta o no es AAAA-MM) → obra visible (404) → residente de esa obra (403).
     */
    @GetMapping("/{id}/material-previsto")
    @PreAuthorize("hasAnyRole('admin','gerente','residente')")
    public MaterialPrevistoResponse materialPrevisto(@PathVariable String id,
            @RequestParam(required = false) String periodo) {
        Periodo leido = periodo(periodo);
        return mapper.aRespuesta(consultarMaterialPrevisto.consultar(uuid(id), leido, usuarioActual.obtener()));
    }

    private static Periodo periodo(String periodo) {
        try {
            return Periodo.de(periodo);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(DATOS_INVALIDOS, "Algunos datos no son válidos.",
                    List.of(new ErrorCampo("periodo", "Debe ser un mes con el formato AAAA-MM")));
        }
    }

    private static EstadoObra estado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return EstadoObra.valueOf(estado.strip());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(DATOS_INVALIDOS, "Algunos datos no son válidos.",
                    List.of(new ErrorCampo("estado", "Debe ser uno de " + Arrays.stream(EstadoObra.values())
                            .map(Enum::name).collect(Collectors.joining(", ")))));
        }
    }

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "Obra no encontrada");
        }
    }
}
