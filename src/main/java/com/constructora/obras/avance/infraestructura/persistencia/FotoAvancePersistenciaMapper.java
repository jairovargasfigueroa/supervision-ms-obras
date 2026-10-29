package com.constructora.obras.avance.infraestructura.persistencia;

import com.constructora.obras.avance.dominio.FotoAvance;
import org.mapstruct.Mapper;

/** Dominio ↔ entidad JPA de foto_avance. */
@Mapper
public interface FotoAvancePersistenciaMapper {

    FotoAvanceEntity aEntidad(FotoAvance foto);

    FotoAvance aDominio(FotoAvanceEntity entidad);
}
