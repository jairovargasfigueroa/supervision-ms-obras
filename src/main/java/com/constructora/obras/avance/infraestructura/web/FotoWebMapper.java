package com.constructora.obras.avance.infraestructura.web;

import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso.FotoListada;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso.DeteccionRecibida;
import com.constructora.obras.avance.infraestructura.web.dto.FotoListadaResponse;
import com.constructora.obras.avance.infraestructura.web.dto.FotoPaginaResponse;
import com.constructora.obras.avance.infraestructura.web.dto.FotoResponse;
import com.constructora.obras.avance.infraestructura.web.dto.RegistrarFotoRequest;
import com.constructora.obras.avance.infraestructura.web.dto.UrlSubidaResponse;
import com.constructora.obras.compartido.dominio.Pagina;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tools.jackson.databind.JsonNode;

/** DTO de la API ↔ comandos y resultados de los casos de uso de fotos de avance. */
@Mapper
public interface FotoWebMapper {

    RegistrarFotoCasoUso.Comando aComando(UUID obraId, RegistrarFotoRequest pedido);

    UrlSubidaResponse aRespuesta(RegistrarFotoCasoUso.UrlSubida urlSubida);

    @Mapping(target = "id", source = "foto.id")
    @Mapping(target = "partidaId", source = "foto.partidaId")
    @Mapping(target = "registradoPor", source = "foto.registradoPor")
    @Mapping(target = "registradoEn", source = "foto.registradoEn")
    @Mapping(target = "publicadaCliente", source = "foto.publicadaCliente")
    @Mapping(target = "deteccion", source = "foto.deteccion")
    FotoResponse aRespuesta(RegistrarFotoCasoUso.Registro registro);

    @Mapping(target = "id", source = "foto.id")
    @Mapping(target = "partidaId", source = "foto.partidaId")
    @Mapping(target = "registradoPor", source = "foto.registradoPor")
    @Mapping(target = "registradoEn", source = "foto.registradoEn")
    @Mapping(target = "publicadaCliente", source = "foto.publicadaCliente")
    @Mapping(target = "deteccion", source = "foto.deteccion")
    FotoListadaResponse aRespuesta(FotoListada listada);

    default FotoPaginaResponse aRespuesta(Pagina<FotoListada> pagina) {
        return new FotoPaginaResponse(pagina.items().stream().map(this::aRespuesta).toList(), pagina.total(),
                pagina.pagina(), pagina.tamano());
    }

    /**
     * 301 · La detección tal como llegó: ausente o null → null (foto no analizada). Si llegó, cada campo que falta o
     * no es del tipo esperado (un conteo que no es entero, una versión que no es texto) va como null y el dominio la
     * descarta; si deteccion no es un objeto, se descarta entera.
     */
    default DeteccionRecibida aDeteccion(JsonNode deteccion) {
        if (deteccion == null || deteccion.isNull() || deteccion.isMissingNode()) {
            return null;
        }
        if (!deteccion.isObject()) {
            return new DeteccionRecibida(null, null, null, null);
        }
        return new DeteccionRecibida(entero(deteccion.get("personas")), entero(deteccion.get("sinCasco")),
                entero(deteccion.get("sinChaleco")), texto(deteccion.get("versionModelo")));
    }

    private static Integer entero(JsonNode valor) {
        return valor != null && valor.isIntegralNumber() && valor.canConvertToInt() ? valor.intValue() : null;
    }

    private static String texto(JsonNode valor) {
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }
}
