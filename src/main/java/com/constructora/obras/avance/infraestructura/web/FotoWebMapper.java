package com.constructora.obras.avance.infraestructura.web;

import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso.FotoListada;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.avance.infraestructura.web.dto.FotoListadaResponse;
import com.constructora.obras.avance.infraestructura.web.dto.FotoPaginaResponse;
import com.constructora.obras.avance.infraestructura.web.dto.FotoResponse;
import com.constructora.obras.avance.infraestructura.web.dto.RegistrarFotoRequest;
import com.constructora.obras.avance.infraestructura.web.dto.UrlSubidaResponse;
import com.constructora.obras.compartido.dominio.Pagina;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** DTO de la API ↔ comandos y resultados de los casos de uso de fotos de avance. */
@Mapper
public interface FotoWebMapper {

    RegistrarFotoCasoUso.Comando aComando(UUID obraId, RegistrarFotoRequest pedido);

    UrlSubidaResponse aRespuesta(RegistrarFotoCasoUso.UrlSubida urlSubida);

    FotoResponse aRespuesta(FotoAvance foto);

    @Mapping(target = "id", source = "foto.id")
    @Mapping(target = "partidaId", source = "foto.partidaId")
    @Mapping(target = "registradoPor", source = "foto.registradoPor")
    @Mapping(target = "registradoEn", source = "foto.registradoEn")
    @Mapping(target = "publicadaCliente", source = "foto.publicadaCliente")
    FotoListadaResponse aRespuesta(FotoListada listada);

    default FotoPaginaResponse aRespuesta(Pagina<FotoListada> pagina) {
        return new FotoPaginaResponse(pagina.items().stream().map(this::aRespuesta).toList(), pagina.total(),
                pagina.pagina(), pagina.tamano());
    }
}
