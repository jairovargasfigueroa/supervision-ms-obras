package com.constructora.obras.diario.infraestructura.web;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.diario.aplicacion.puerto.entrada.ConsultarDiarioCasoUso;
import com.constructora.obras.diario.aplicacion.puerto.entrada.RegistrarEntradaDiarioCasoUso;
import com.constructora.obras.diario.infraestructura.web.dto.EntradaDiarioResponse;
import com.constructora.obras.diario.infraestructura.web.dto.RegistrarEntradaDiarioRequest;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import jakarta.validation.Valid;
import java.util.List;
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
 * 103 · Diario de obra. Registra el maestro asignado (lo llama Sincronización con el token del maestro); el rol
 * también se exige en ConfiguracionSeguridad para que el 403 llegue antes que la validación del cuerpo. Lo consultan
 * el maestro y el residente asignados, admin y gerente; el cliente recibe 404 (lo decide el caso de uso).
 */
@RestController
@RequestMapping("/obras/{obraId}/diario")
public class DiarioController {

    private static final String TODOS_LOS_ROLES =
            "hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')";

    private final RegistrarEntradaDiarioCasoUso registrarEntrada;
    private final ConsultarDiarioCasoUso consultarDiario;
    private final UsuarioActual usuarioActual;
    private final DiarioWebMapper mapper;

    public DiarioController(RegistrarEntradaDiarioCasoUso registrarEntrada, ConsultarDiarioCasoUso consultarDiario,
            UsuarioActual usuarioActual, DiarioWebMapper mapper) {
        this.registrarEntrada = registrarEntrada;
        this.consultarDiario = consultarDiario;
        this.usuarioActual = usuarioActual;
        this.mapper = mapper;
    }

    /** Registra la entrada: 201, o 200 con la entrada guardada si el id ya estaba (idempotencia). */
    @PostMapping
    @PreAuthorize("hasRole('maestro')")
    public ResponseEntity<EntradaDiarioResponse> registrar(@PathVariable String obraId,
            @Valid @RequestBody RegistrarEntradaDiarioRequest pedido) {
        RegistrarEntradaDiarioCasoUso.Registro registro = registrarEntrada.registrar(
                mapper.aComando(uuid(obraId), pedido), usuarioActual.obtener());
        return ResponseEntity.status(registro.creada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mapper.aRespuesta(registro.entrada()));
    }

    /** Entradas entre desde y hasta (AAAA-MM-DD, incluidas; por defecto los últimos 30 días), la más reciente primero. */
    @GetMapping
    @PreAuthorize(TODOS_LOS_ROLES)
    public List<EntradaDiarioResponse> listar(@PathVariable String obraId,
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        return mapper.aRespuesta(consultarDiario.listar(new ConsultarDiarioCasoUso.Consulta(uuid(obraId), desde,
                hasta), usuarioActual.obtener()));
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
