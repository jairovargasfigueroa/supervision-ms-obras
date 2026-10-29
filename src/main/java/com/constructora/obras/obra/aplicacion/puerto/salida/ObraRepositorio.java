package com.constructora.obras.obra.aplicacion.puerto.salida;

import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.dominio.Obra;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObraRepositorio {

    /** Guarda una obra nueva o los cambios de una existente (estado, contrato, motivo de rechazo). */
    void guardar(Obra obra);

    Optional<Obra> buscarPorId(UUID id);

    /** Todas las obras que cumplen el filtro, por nombre. */
    List<Obra> listarTodas(FiltroObras filtro);

    /**
     * Obras que cumplen el filtro donde el usuario está asignado en alguno de los papeles indicados, por nombre.
     */
    List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro, boolean comoAlmacenero,
            boolean comoCliente, FiltroObras filtro);
}
