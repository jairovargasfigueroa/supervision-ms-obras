package com.constructora.obras.avance.aplicacion.puerto.entrada;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import com.constructora.obras.compartido.dominio.Usuario;
import java.util.Set;
import java.util.UUID;

/**
 * Fotos de avance de una obra (005, 006, 007, 008), paginadas, con URL de descarga firmada. Las ve quien ve la obra;
 * el cliente solo recibe las publicadas.
 */
public interface ConsultarFotosCasoUso {

    /** Campos por los que se puede ordenar la lista; por defecto registradoEn desc. */
    Set<String> ORDENES = Set.of("registradoEn");
    String ORDEN_POR_DEFECTO = "registradoEn";

    Pagina<FotoListada> listar(Consulta consulta, Usuario usuario);

    /** partidaId es opcional (null: todas las partidas). */
    record Consulta(UUID obraId, UUID partidaId, Paginacion paginacion) {
    }

    record FotoListada(FotoAvance foto, String urlDescarga) {
    }
}
