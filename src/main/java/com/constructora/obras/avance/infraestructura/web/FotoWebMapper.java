package com.constructora.obras.avance.infraestructura.web;

import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.avance.infraestructura.web.dto.FotoResponse;
import com.constructora.obras.avance.infraestructura.web.dto.RegistrarFotoRequest;
import com.constructora.obras.avance.infraestructura.web.dto.UrlSubidaResponse;
import java.util.UUID;
import org.mapstruct.Mapper;

/** DTO de la API ↔ comandos y resultados de los casos de uso de fotos de avance. */
@Mapper
public interface FotoWebMapper {

    RegistrarFotoCasoUso.Comando aComando(UUID obraId, RegistrarFotoRequest pedido);

    UrlSubidaResponse aRespuesta(RegistrarFotoCasoUso.UrlSubida urlSubida);

    FotoResponse aRespuesta(FotoAvance foto);
}
