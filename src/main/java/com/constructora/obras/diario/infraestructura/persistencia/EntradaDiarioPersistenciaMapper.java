package com.constructora.obras.diario.infraestructura.persistencia;

import com.constructora.obras.diario.dominio.EntradaDiario;
import org.mapstruct.Mapper;

/** Entidad JPA de entrada_diario → dominio (el clima se guarda con su nombre). */
@Mapper
public interface EntradaDiarioPersistenciaMapper {

    EntradaDiario aDominio(EntradaDiarioEntity entidad);
}
