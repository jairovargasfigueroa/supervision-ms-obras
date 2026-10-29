package com.constructora.obras.avance.infraestructura.web;

import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso;
import com.constructora.obras.avance.infraestructura.web.dto.FotoPaginaResponse;
import com.constructora.obras.avance.infraestructura.web.dto.FotoResponse;
import com.constructora.obras.avance.infraestructura.web.dto.RegistrarFotoRequest;
import com.constructora.obras.avance.infraestructura.web.dto.UrlSubidaRequest;
import com.constructora.obras.avance.infraestructura.web.dto.UrlSubidaResponse;
import com.constructora.obras.compartido.dominio.Paginacion;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 005 · Fotos de avance. Registran el maestro y el residente asignados; el rol también se exige en
 * ConfiguracionSeguridad para que el 403 llegue antes que la validación del cuerpo.
 */
@RestController
@RequestMapping("/obras/{obraId}/fotos")
public class FotoController {

    private static final String REGISTRAN = "hasAnyRole('maestro','residente')";
    private static final String TODOS_LOS_ROLES =
            "hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')";

    private final RegistrarFotoCasoUso registrarFoto;
    private final ConsultarFotosCasoUso consultarFotos;
    private final UsuarioActual usuarioActual;
    private final FotoWebMapper mapper;

    public FotoController(RegistrarFotoCasoUso registrarFoto, ConsultarFotosCasoUso consultarFotos,
            UsuarioActual usuarioActual, FotoWebMapper mapper) {
        this.registrarFoto = registrarFoto;
        this.consultarFotos = consultarFotos;
        this.usuarioActual = usuarioActual;
        this.mapper = mapper;
    }

    /**
     * Fotos de la obra, paginadas y de la más reciente a la más antigua (por defecto), con URL de descarga firmada.
     * Las ve quien ve la obra; el cliente, solo las publicadas. Los filtros desde y hasta son de la 007: por ahora se
     * aceptan y no filtran.
     */
    @GetMapping
    @PreAuthorize(TODOS_LOS_ROLES)
    public FotoPaginaResponse listar(@PathVariable String obraId, @RequestParam(required = false) UUID partidaId,
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamano,
            @RequestParam(required = false) String orden, @RequestParam(required = false) String direccion) {
        UUID obra = uuid(obraId);
        Paginacion paginacion = Paginacion.de(pagina, tamano, orden, direccion, ConsultarFotosCasoUso.ORDENES,
                ConsultarFotosCasoUso.ORDEN_POR_DEFECTO);
        return mapper.aRespuesta(consultarFotos.listar(
                new ConsultarFotosCasoUso.Consulta(obra, partidaId, paginacion), usuarioActual.obtener()));
    }

    /** URL firmada para subir la foto a S3 (PUT, image/jpeg, 10 minutos). Sin S3 configurado: 503. */
    @PostMapping("/url-subida")
    @PreAuthorize(REGISTRAN)
    public UrlSubidaResponse urlSubida(@PathVariable String obraId, @Valid @RequestBody UrlSubidaRequest pedido) {
        return mapper.aRespuesta(registrarFoto.prepararSubida(uuid(obraId), pedido.fotoId(), usuarioActual.obtener()));
    }

    /** Registra la foto ya subida: 201, o 200 con el registro existente si el id ya estaba (idempotencia). */
    @PostMapping
    @PreAuthorize(REGISTRAN)
    public ResponseEntity<FotoResponse> registrar(@PathVariable String obraId,
            @Valid @RequestBody RegistrarFotoRequest pedido) {
        RegistrarFotoCasoUso.Registro registro = registrarFoto.registrar(mapper.aComando(uuid(obraId), pedido),
                usuarioActual.obtener());
        return ResponseEntity.status(registro.creada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mapper.aRespuesta(registro.foto()));
    }

    /** Un id de obra que no es UUID responde como una obra que no existe. */
    static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "Obra no encontrada");
        }
    }
}
