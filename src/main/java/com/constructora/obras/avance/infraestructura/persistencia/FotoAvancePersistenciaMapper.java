package com.constructora.obras.avance.infraestructura.persistencia;

import com.constructora.obras.avance.dominio.DeteccionEpp;
import com.constructora.obras.avance.dominio.FotoAvance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Dominio ↔ entidad JPA de foto_avance. La detección (301) va en cuatro columnas planas. */
@Mapper
public interface FotoAvancePersistenciaMapper {

    @Mapping(target = "personasDetectadas", source = "deteccion.personas")
    @Mapping(target = "sinCasco", source = "deteccion.sinCasco")
    @Mapping(target = "sinChaleco", source = "deteccion.sinChaleco")
    @Mapping(target = "versionModeloEpp", source = "deteccion.versionModelo")
    FotoAvanceEntity aEntidad(FotoAvance foto);

    @Mapping(target = "deteccion", source = ".")
    FotoAvance aDominio(FotoAvanceEntity entidad);

    /** Las cuatro columnas se guardan juntas; si falta alguna, la foto no tiene detección. */
    default DeteccionEpp deteccion(FotoAvanceEntity entidad) {
        if (entidad.getPersonasDetectadas() == null || entidad.getSinCasco() == null
                || entidad.getSinChaleco() == null || entidad.getVersionModeloEpp() == null) {
            return null;
        }
        return new DeteccionEpp(entidad.getPersonasDetectadas(), entidad.getSinCasco(), entidad.getSinChaleco(),
                entidad.getVersionModeloEpp());
    }
}
