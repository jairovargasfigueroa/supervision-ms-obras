package com.constructora.obras.planilla.infraestructura.persistencia;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.LineaPlanilla;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.TotalesPlanilla;
import org.mapstruct.Mapper;

/**
 * Dominio ↔ entidad JPA de la planilla. Se arma a mano (métodos por defecto) porque el agregado no expone setters ni
 * constructor público y los montos son Dinero.
 */
@Mapper
public interface PlanillaPersistenciaMapper {

    /** Planilla nueva: datos fijos más lo que cambia al guardar. */
    default PlanillaEntity aEntidad(Planilla planilla) {
        PlanillaEntity entidad = new PlanillaEntity();
        entidad.setId(planilla.id());
        entidad.setObraId(planilla.obraId());
        entidad.setNumero(planilla.numero());
        entidad.setPeriodo(planilla.periodo().primerDia());
        entidad.setObservacion(planilla.observacion());
        entidad.setCobroId(planilla.cobroId());
        entidad.setCobroNumero(planilla.cobroNumero());
        actualizar(planilla, entidad);
        return entidad;
    }

    /** Lo que cambia al guardar o presentar (007): estado, líneas, totales y fotos adjuntas. */
    default void actualizar(Planilla planilla, PlanillaEntity entidad) {
        entidad.setEstado(planilla.estado().name());
        TotalesPlanilla totales = planilla.totales();
        entidad.setSubtotal(totales.subtotal().valor());
        entidad.setDescuentoAnticipo(totales.descuentoAnticipo().valor());
        entidad.setRetencion(totales.retencion().valor());
        entidad.setNeto(totales.neto().valor());
        entidad.getLineas().clear();
        for (LineaPlanilla linea : planilla.lineas()) {
            LineaPlanillaEntity fila = new LineaPlanillaEntity();
            fila.setPartidaId(linea.partidaId());
            fila.setCantidadPeriodo(linea.cantidadPeriodo());
            fila.setMonto(linea.monto().valor());
            entidad.getLineas().add(fila);
        }
        entidad.getFotoIds().retainAll(planilla.fotoIds());
        entidad.getFotoIds().addAll(planilla.fotoIds());
    }

    default Planilla aDominio(PlanillaEntity entidad) {
        return Planilla.reconstruir(entidad.getId(), entidad.getObraId(), entidad.getNumero(),
                Periodo.de(entidad.getPeriodo()), EstadoPlanilla.valueOf(entidad.getEstado()),
                entidad.getLineas().stream()
                        .map(fila -> new LineaPlanilla(fila.getPartidaId(), fila.getCantidadPeriodo(),
                                Dinero.de(fila.getMonto())))
                        .toList(),
                new TotalesPlanilla(Dinero.de(entidad.getSubtotal()), Dinero.de(entidad.getDescuentoAnticipo()),
                        Dinero.de(entidad.getRetencion()), Dinero.de(entidad.getNeto())),
                entidad.getObservacion(), entidad.getCobroId(), entidad.getCobroNumero(), entidad.getVersion(),
                entidad.getFotoIds());
    }
}
