package com.constructora.obras.avance.aplicacion.puerto.salida;

import com.constructora.obras.avance.dominio.FotoAvance;
import java.util.Optional;
import java.util.UUID;

public interface FotoAvanceRepositorio {

    /** Guarda una foto nueva. */
    void guardar(FotoAvance foto);

    Optional<FotoAvance> buscarPorId(UUID id);
}
