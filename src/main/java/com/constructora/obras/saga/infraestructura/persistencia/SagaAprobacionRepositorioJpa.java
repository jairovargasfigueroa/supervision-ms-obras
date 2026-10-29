package com.constructora.obras.saga.infraestructura.persistencia;

import com.constructora.obras.saga.aplicacion.puerto.salida.SagaAprobacionRepositorio;
import com.constructora.obras.saga.dominio.EstadoSaga;
import com.constructora.obras.saga.dominio.PasoSaga;
import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adaptador de SagaAprobacionRepositorio sobre JPA. El detalle se guarda como JSON en camelCase: reservaId, cobroId,
 * cobroNumero y motivo, solo los que ya hay.
 */
@Repository
public class SagaAprobacionRepositorioJpa implements SagaAprobacionRepositorio {

    private final SagaAprobacionJpaRepository jpa;
    private final JsonMapper json;

    public SagaAprobacionRepositorioJpa(SagaAprobacionJpaRepository jpa, JsonMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    public void guardar(SagaAprobacion saga) {
        SagaAprobacionEntity entidad = jpa.findById(saga.id())
                .orElseGet(() -> new SagaAprobacionEntity(saga.id(), saga.planillaId()));
        entidad.setPasoActual(saga.paso().name());
        entidad.setEstado(saga.estado().name());
        entidad.setDetalle(detalle(saga));
        entidad.setVenceEn(saga.venceEn());
        entidad.setActualizadaEn(saga.actualizadaEn());
        jpa.saveAndFlush(entidad);
    }

    @Override
    public Optional<SagaAprobacion> buscarParaActualizar(UUID id) {
        return jpa.bloquear(id).map(this::aDominio);
    }

    @Override
    public List<UUID> vencidas(Instant ahora) {
        return jpa.buscarVencidas(ahora);
    }

    private String detalle(SagaAprobacion saga) {
        ObjectNode detalle = json.createObjectNode();
        if (saga.reservaId() != null) {
            detalle.put("reservaId", saga.reservaId().toString());
        }
        if (saga.cobroId() != null) {
            detalle.put("cobroId", saga.cobroId().toString());
        }
        if (saga.cobroNumero() != null) {
            detalle.put("cobroNumero", saga.cobroNumero());
        }
        if (saga.motivo() != null) {
            detalle.put("motivo", saga.motivo());
        }
        return json.writeValueAsString(detalle);
    }

    private SagaAprobacion aDominio(SagaAprobacionEntity entidad) {
        JsonNode detalle = entidad.getDetalle() == null ? json.createObjectNode() : json.readTree(entidad.getDetalle());
        return SagaAprobacion.reconstruir(entidad.getId(), entidad.getPlanillaId(),
                PasoSaga.valueOf(entidad.getPasoActual()), EstadoSaga.valueOf(entidad.getEstado()),
                uuid(detalle, "reservaId"), uuid(detalle, "cobroId"), texto(detalle, "cobroNumero"),
                texto(detalle, "motivo"), entidad.getVenceEn(), entidad.getActualizadaEn());
    }

    private static UUID uuid(JsonNode detalle, String campo) {
        String valor = texto(detalle, campo);
        return valor == null ? null : UUID.fromString(valor);
    }

    private static String texto(JsonNode detalle, String campo) {
        JsonNode valor = detalle.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }
}
