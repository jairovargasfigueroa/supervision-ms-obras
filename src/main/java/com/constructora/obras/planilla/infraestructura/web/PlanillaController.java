package com.constructora.obras.planilla.infraestructura.web;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ElaborarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.RevisarPlanillaCasoUso;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.infraestructura.web.dto.AprobacionResponse;
import com.constructora.obras.planilla.infraestructura.web.dto.CrearPlanillaRequest;
import com.constructora.obras.planilla.infraestructura.web.dto.GuardarPlanillaRequest;
import com.constructora.obras.planilla.infraestructura.web.dto.ObservarPlanillaRequest;
import com.constructora.obras.planilla.infraestructura.web.dto.PlanillaResponse;
import com.constructora.obras.planilla.infraestructura.web.dto.PlanillaResumenResponse;
import com.constructora.obras.planilla.infraestructura.web.dto.PresentarPlanillaRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 007 · Elaborar y presentar planilla. Elabora, guarda y presenta solo el residente asignado; leen los que ven la
 * obra (el cliente, sin los BORRADOR). 008 · El cliente de la obra la aprueba u observa. El rol de las escrituras
 * también se exige en ConfiguracionSeguridad para que el 403 llegue antes que la validación del cuerpo.
 */
@RestController
public class PlanillaController {

    private static final String ELABORA = "hasRole('residente')";
    private static final String REVISA = "hasRole('cliente')";
    private static final String TODOS_LOS_ROLES =
            "hasAnyRole('admin','residente','gerente','maestro','almacenero','cliente')";

    private final ElaborarPlanillaCasoUso elaborar;
    private final ConsultarPlanillaCasoUso consultar;
    private final RevisarPlanillaCasoUso revisar;
    private final UsuarioActual usuarioActual;
    private final PlanillaWebMapper mapper;

    public PlanillaController(ElaborarPlanillaCasoUso elaborar, ConsultarPlanillaCasoUso consultar,
            RevisarPlanillaCasoUso revisar, UsuarioActual usuarioActual, PlanillaWebMapper mapper) {
        this.elaborar = elaborar;
        this.consultar = consultar;
        this.revisar = revisar;
        this.usuarioActual = usuarioActual;
        this.mapper = mapper;
    }

    /**
     * Crea la planilla del periodo en BORRADOR. Orden: rol (403) → periodo (400 DATOS_INVALIDOS si falta o no es
     * AAAA-MM) → obra visible (404) → residente de esa obra (403) → obra en ejecución, planilla abierta, periodo no
     * posterior y mes futuro.
     */
    @PostMapping("/obras/{obraId}/planillas")
    @PreAuthorize(ELABORA)
    public ResponseEntity<PlanillaResponse> crear(@PathVariable String obraId,
            @RequestBody CrearPlanillaRequest pedido) {
        Periodo periodo = periodo(pedido.periodo());
        PlanillaDetalle detalle = elaborar.crear(uuidObra(obraId), periodo, usuarioActual.obtener());
        return ResponseEntity.created(URI.create("/planillas/" + detalle.planilla().id()))
                .body(mapper.aRespuesta(detalle));
    }

    /** Planillas de la obra por número; el cliente (desde el portal) no recibe las BORRADOR. */
    @GetMapping("/obras/{obraId}/planillas")
    @PreAuthorize(TODOS_LOS_ROLES)
    public List<PlanillaResumenResponse> listar(@PathVariable String obraId) {
        return consultar.listar(uuidObra(obraId), usuarioActual.obtener()).stream().map(mapper::aResumen).toList();
    }

    /** Detalle; si no existe, no ve su obra, el id no es un UUID o es un BORRADOR para el cliente: 404. */
    @GetMapping("/planillas/{id}")
    @PreAuthorize(TODOS_LOS_ROLES)
    public PlanillaResponse obtener(@PathVariable String id) {
        return mapper.aRespuesta(consultar.obtener(uuidPlanilla(id), usuarioActual.obtener()));
    }

    /** Guarda el borrador (cantidades y fotos) con bloqueo optimista por versión. */
    @PutMapping("/planillas/{id}")
    @PreAuthorize(ELABORA)
    public PlanillaResponse guardar(@PathVariable String id, @Valid @RequestBody GuardarPlanillaRequest pedido) {
        return mapper.aRespuesta(elaborar.guardar(mapper.aComando(uuidPlanilla(id), pedido),
                usuarioActual.obtener()));
    }

    /** Presenta la planilla (PRESENTADA) y publica sus fotos adjuntas para el cliente. */
    @PostMapping("/planillas/{id}/presentar")
    @PreAuthorize(ELABORA)
    public PlanillaResponse presentar(@PathVariable String id, @Valid @RequestBody PresentarPlanillaRequest pedido) {
        return mapper.aRespuesta(elaborar.presentar(uuidPlanilla(id), pedido.version(), usuarioActual.obtener()));
    }

    /**
     * 008 · El cliente aprueba la planilla PRESENTADA: pasa a APROBANDO y empieza la saga. Orden: rol (403) →
     * planilla visible (404) → cliente de la obra (403) → PRESENTADA (409 PLANILLA_NO_PRESENTADA).
     */
    @PostMapping("/planillas/{id}/aprobar")
    @PreAuthorize(REVISA)
    public ResponseEntity<AprobacionResponse> aprobar(@PathVariable String id) {
        EstadoPlanilla estado = revisar.aprobar(uuidPlanilla(id), usuarioActual.obtener());
        return ResponseEntity.accepted().body(new AprobacionResponse(estado.name()));
    }

    /**
     * 008 · El cliente observa la planilla PRESENTADA con un motivo. Orden: rol (403) → cuerpo JSON (400) → planilla
     * visible (404) → cliente de la obra (403) → PRESENTADA (409) → motivo de 10 a 500 caracteres (422).
     */
    @PostMapping("/planillas/{id}/observar")
    @PreAuthorize(REVISA)
    public PlanillaResponse observar(@PathVariable String id, @RequestBody ObservarPlanillaRequest pedido) {
        return mapper.aRespuesta(revisar.observar(uuidPlanilla(id), pedido.motivo(), usuarioActual.obtener()));
    }

    private static Periodo periodo(String periodo) {
        try {
            return Periodo.de(periodo);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("DATOS_INVALIDOS", "Algunos datos no son válidos.",
                    List.of(new ErrorCampo("periodo", "Debe ser un mes con el formato AAAA-MM")));
        }
    }

    private static UUID uuidObra(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "Obra no encontrada");
        }
    }

    private static UUID uuidPlanilla(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(ConsultarPlanillaCasoUso.PLANILLA_NO_ENCONTRADA, "Planilla no encontrada");
        }
    }
}
