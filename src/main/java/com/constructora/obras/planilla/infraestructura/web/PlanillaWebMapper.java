package com.constructora.obras.planilla.infraestructura.web;

import com.constructora.obras.planilla.aplicacion.puerto.entrada.ElaborarPlanillaCasoUso.Guardado;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.Planilla.CantidadLinea;
import com.constructora.obras.planilla.dominio.Planilla.LineaDetalle;
import com.constructora.obras.planilla.dominio.TotalesPlanilla;
import com.constructora.obras.planilla.infraestructura.web.dto.GuardarPlanillaRequest;
import com.constructora.obras.planilla.infraestructura.web.dto.PlanillaResponse;
import com.constructora.obras.planilla.infraestructura.web.dto.PlanillaResumenResponse;
import java.util.UUID;
import org.mapstruct.Mapper;

/** DTO de la API ↔ comandos y resultados de los casos de uso de planillas. */
@Mapper
public interface PlanillaWebMapper {

    default Guardado aComando(UUID planillaId, GuardarPlanillaRequest pedido) {
        return new Guardado(planillaId, pedido.version(), pedido.lineas().stream()
                .map(linea -> new CantidadLinea(linea.partidaId(), linea.cantidadPeriodo()))
                .toList(), pedido.fotoIds());
    }

    default PlanillaResumenResponse aResumen(Planilla planilla) {
        return new PlanillaResumenResponse(planilla.id(), planilla.numero(), planilla.periodo().toString(),
                planilla.estado().name(), planilla.totales().neto().valor(), planilla.cobroId(),
                planilla.cobroNumero());
    }

    default PlanillaResponse aRespuesta(PlanillaDetalle detalle) {
        Planilla planilla = detalle.planilla();
        TotalesPlanilla totales = planilla.totales();
        return new PlanillaResponse(planilla.id(), planilla.obraId(), planilla.numero(),
                planilla.periodo().toString(), planilla.estado().name(), planilla.version(),
                detalle.lineas().stream().map(this::aLinea).toList(), totales.subtotal().valor(),
                totales.descuentoAnticipo().valor(), totales.retencion().valor(), totales.neto().valor(),
                detalle.anticipoPct(), detalle.retencionPct(), planilla.observacion(), planilla.cobroId(),
                planilla.cobroNumero(), planilla.fotoIds());
    }

    default PlanillaResponse.Linea aLinea(LineaDetalle linea) {
        return new PlanillaResponse.Linea(linea.partida().id(), linea.partida().codigo(), linea.partida().nombre(),
                linea.partida().unidad(), linea.partida().precioUnitario(), linea.partida().cantidadContratada(),
                linea.acumuladoAnterior(), linea.cantidadPeriodo(), linea.acumuladoTotal(), linea.saldo(),
                linea.monto().valor());
    }
}
