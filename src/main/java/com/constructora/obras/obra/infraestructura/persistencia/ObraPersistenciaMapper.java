package com.constructora.obras.obra.infraestructura.persistencia;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.InsumoPrevisto;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.obra.dominio.Zona;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * Dominio ↔ entidades JPA. Partidas e insumos (records) los genera MapStruct; la obra se arma a mano porque el
 * agregado no expone setters ni constructor público.
 */
@Mapper
public interface ObraPersistenciaMapper {

    PartidaEntity aEntidad(Partida partida);

    InsumoPrevistoEntity aEntidad(InsumoPrevisto insumo);

    Partida aDominio(PartidaEntity entidad);

    InsumoPrevisto aDominio(InsumoPrevistoEntity entidad);

    List<PartidaEntity> partidasAEntidad(List<Partida> partidas);

    List<Partida> partidasADominio(List<PartidaEntity> partidas);

    default ObraEntity aEntidad(Obra obra) {
        ObraEntity entidad = new ObraEntity();
        entidad.setId(obra.id());
        entidad.setNombre(obra.nombre());
        entidad.setUbicacion(obra.ubicacion());
        entidad.setZona(obra.zona() == null ? null : obra.zona().name());
        entidad.setFechaInicio(obra.fechaInicio());
        Asignaciones asignaciones = obra.asignaciones();
        entidad.setResidenteUsuarioId(asignaciones.residenteId());
        entidad.setMaestroUsuarioId(asignaciones.maestroId());
        entidad.setAlmaceneroUsuarioId(asignaciones.almaceneroId());
        entidad.setClienteUsuarioId(asignaciones.clienteId());
        ContratoCopia contrato = obra.contrato();
        entidad.setMontoContrato(contrato.monto().valor());
        entidad.setPlazoDias(contrato.plazoDias());
        entidad.setAnticipoPct(contrato.anticipoPct());
        entidad.setRetencionPct(contrato.retencionPct());
        entidad.setPartidas(partidasAEntidad(obra.partidas()));
        actualizar(obra, entidad);
        return entidad;
    }

    /** Lo que puede cambiar después de registrada (002): estado, contrato y motivo del rechazo. */
    default void actualizar(Obra obra, ObraEntity entidad) {
        entidad.setEstado(obra.estado().name());
        entidad.setContratoId(obra.contrato().contratoId());
        entidad.setMotivoRechazoContrato(obra.motivoRechazoContrato());
    }

    default Obra aDominio(ObraEntity entidad) {
        return Obra.reconstruir(entidad.getId(), entidad.getNombre(), entidad.getUbicacion(),
                entidad.getZona() == null ? null : Zona.valueOf(entidad.getZona()), entidad.getFechaInicio(),
                EstadoObra.valueOf(entidad.getEstado()),
                new Asignaciones(entidad.getResidenteUsuarioId(), entidad.getMaestroUsuarioId(),
                        entidad.getAlmaceneroUsuarioId(), entidad.getClienteUsuarioId()),
                new ContratoCopia(entidad.getContratoId(), Dinero.de(entidad.getMontoContrato()),
                        entidad.getPlazoDias(), entidad.getAnticipoPct(), entidad.getRetencionPct()),
                entidad.getMotivoRechazoContrato(), partidasADominio(entidad.getPartidas()));
    }
}
