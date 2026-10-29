package com.constructora.obras.avance.aplicacion.puerto.salida;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoAvanceRepositorio {

    /** Guarda una foto nueva. */
    void guardar(FotoAvance foto);

    Optional<FotoAvance> buscarPorId(UUID id);

    /** 007 · Las fotos que existen entre esos ids (las que no existen no figuran). */
    List<FotoAvance> buscarPorIds(Collection<UUID> ids);

    /** 007 · Marca publicada_cliente = true en esas fotos (al presentar la planilla que las adjunta). */
    void publicar(Collection<UUID> ids);

    /**
     * Fotos de la obra, ordenadas según la paginación (y por id para desempatar).
     *
     * @param partidaId      null: todas las partidas
     * @param soloPublicadas true: solo las que tienen publicada_cliente
     */
    Pagina<FotoAvance> listar(UUID obraId, UUID partidaId, boolean soloPublicadas, Paginacion paginacion);
}
