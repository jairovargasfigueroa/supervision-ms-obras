package com.constructora.obras.diario.infraestructura.web;

import com.constructora.obras.diario.aplicacion.puerto.entrada.RegistrarEntradaDiarioCasoUso;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.diario.infraestructura.web.dto.EntradaDiarioResponse;
import com.constructora.obras.diario.infraestructura.web.dto.RegistrarEntradaDiarioRequest;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;

/** DTO de la API ↔ comandos y resultados de los casos de uso del diario de obra. */
@Mapper
public interface DiarioWebMapper {

    RegistrarEntradaDiarioCasoUso.Comando aComando(UUID obraId, RegistrarEntradaDiarioRequest pedido);

    EntradaDiarioResponse aRespuesta(EntradaDiario entrada);

    List<EntradaDiarioResponse> aRespuesta(List<EntradaDiario> entradas);
}
