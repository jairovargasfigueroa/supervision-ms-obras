package com.constructora.obras.avance.aplicacion.puerto.entrada;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Usuario;
import java.time.Instant;
import java.util.UUID;

/**
 * 005 · Registrar foto de avance: la app pide una URL firmada, sube el archivo a S3 y registra la foto. Solo el
 * maestro y el residente asignados: si el usuario no ve la obra, OBRA_NO_ENCONTRADA; si la ve con otro papel,
 * SIN_PERMISO.
 */
public interface RegistrarFotoCasoUso {

    String SIN_PERMISO = "SIN_PERMISO";

    /** URL firmada (PUT, image/jpeg, 10 minutos) para subir la foto con la clave fotos/{obraId}/{fotoId}.jpg. */
    UrlSubida prepararSubida(UUID obraId, UUID fotoId, Usuario usuario);

    /** Registra la foto; si el id ya existe devuelve el registro guardado (creada = false). */
    Registro registrar(Comando comando, Usuario usuario);

    record Comando(UUID obraId, UUID id, UUID partidaId, String claveArchivo, Instant tomadaEn) {
    }

    record UrlSubida(String url, String claveArchivo, Instant expiraEn) {
    }

    record Registro(FotoAvance foto, boolean creada) {
    }
}
