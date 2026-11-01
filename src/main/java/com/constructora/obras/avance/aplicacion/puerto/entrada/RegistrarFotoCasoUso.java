package com.constructora.obras.avance.aplicacion.puerto.entrada;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Usuario;
import java.time.Instant;
import java.util.List;
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

    /**
     * Registra la foto; si el id ya existe devuelve el registro guardado (creada = false) sin tocar su detección. Una
     * detección que no es válida (301) no impide el registro: la foto se guarda sin ella y el registro lleva el aviso
     * DETECCION_DESCARTADA.
     */
    Registro registrar(Comando comando, Usuario usuario);

    /** @param deteccion detección de casco y chaleco tal como llegó (301); null si la foto no se analizó */
    record Comando(UUID obraId, UUID id, UUID partidaId, String claveArchivo, Instant tomadaEn,
            DeteccionRecibida deteccion) {
    }

    /**
     * 301 · Detección enviada por el celular, sin validar. Un valor null es un campo que falta o que no es del tipo
     * esperado (lo decide el adaptador web).
     */
    record DeteccionRecibida(Integer personas, Integer sinCasco, Integer sinChaleco, String versionModelo) {
    }

    record UrlSubida(String url, String claveArchivo, Instant expiraEn) {
    }

    /** @param avisos códigos de aviso para la respuesta (301: DETECCION_DESCARTADA); vacía si no hay */
    record Registro(FotoAvance foto, boolean creada, List<String> avisos) {

        public Registro {
            avisos = List.copyOf(avisos);
        }
    }
}
