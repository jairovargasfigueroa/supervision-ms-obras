package com.constructora.obras.obra.infraestructura.web;

import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.InsumoPrevisto;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResponse;
import com.constructora.obras.obra.infraestructura.web.dto.ObraResumenResponse;
import com.constructora.obras.obra.infraestructura.web.dto.RegistrarObraRequest;
import java.util.List;
import org.mapstruct.Mapper;

/** DTO de la API ↔ comandos y resultados de los casos de uso. */
@Mapper
public interface ObraWebMapper {

    RegistrarObraCasoUso.Comando aComando(RegistrarObraRequest pedido);

    Asignaciones aAsignaciones(RegistrarObraRequest.AsignacionesRequest asignaciones);

    RegistrarObraCasoUso.ContratoComando aContrato(RegistrarObraRequest.ContratoRequest contrato);

    RegistrarObraCasoUso.PartidaComando aPartida(RegistrarObraRequest.PartidaRequest partida);

    RegistrarObraCasoUso.InsumoComando aInsumo(RegistrarObraRequest.InsumoRequest insumo);

    ObraResponse.Asignaciones aRespuesta(Asignaciones asignaciones);

    ObraResponse.Insumo aRespuesta(InsumoPrevisto insumo);

    List<ObraResponse.Insumo> insumosARespuesta(List<InsumoPrevisto> insumos);

    default ObraResponse aRespuesta(ObraDetalle detalle) {
        Obra obra = detalle.obra();
        Obra.Indicadores indicadores = detalle.indicadores();
        ContratoCopia contrato = obra.contrato();
        List<ObraResponse.Partida> partidas = obra.partidas().stream()
                .map(partida -> aRespuesta(partida, indicadores))
                .toList();
        return new ObraResponse(obra.id(), obra.nombre(), obra.ubicacion(), obra.estado().name(),
                obra.motivoRechazoContrato(), obra.fechaInicio(), aRespuesta(obra.asignaciones()),
                new ObraResponse.Contrato(contrato.contratoId(), contrato.monto().valor(), contrato.plazoDias(),
                        contrato.anticipoPct(), contrato.retencionPct()),
                indicadores.avancePct(), indicadores.plazoPct(), indicadores.enRiesgo(), partidas);
    }

    /** Fila de GET /obras. Las planillas llegan con la 007: hasta entonces no hay última planilla. */
    default ObraResumenResponse aResumen(ObraDetalle detalle) {
        Obra obra = detalle.obra();
        Obra.Indicadores indicadores = detalle.indicadores();
        return new ObraResumenResponse(obra.id(), obra.nombre(), obra.ubicacion(), obra.estado().name(),
                obra.fechaInicio(), indicadores.avancePct(), indicadores.plazoPct(), indicadores.enRiesgo(), null);
    }

    private ObraResponse.Partida aRespuesta(Partida partida, Obra.Indicadores indicadores) {
        return new ObraResponse.Partida(partida.id(), partida.codigo(), partida.nombre(), partida.unidad(),
                partida.cantidadContratada(), partida.precioUnitario(), partida.meses(),
                indicadores.ejecutadaPorPartida().get(partida.id()), indicadores.avancePorPartida().get(partida.id()),
                insumosARespuesta(partida.insumos()));
    }
}
