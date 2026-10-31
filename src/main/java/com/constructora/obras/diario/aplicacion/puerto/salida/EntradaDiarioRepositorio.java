package com.constructora.obras.diario.aplicacion.puerto.salida;

import com.constructora.obras.diario.dominio.EntradaDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntradaDiarioRepositorio {

    /**
     * Guarda una entrada nueva salvo que ya exista otra con ese id o con esa obra y fecha (la base lo comprueba con
     * su clave primaria y su restricción única, también si las dos llegan a la vez).
     *
     * @return false si no se guardó porque ya existía
     */
    boolean guardarSiNoExiste(EntradaDiario entrada);

    Optional<EntradaDiario> buscarPorId(UUID id);

    Optional<EntradaDiario> buscarPorObraYFecha(UUID obraId, LocalDate fecha);

    /** Entradas de la obra con fecha entre desde y hasta (incluidas), de la más reciente a la más antigua. */
    List<EntradaDiario> listar(UUID obraId, LocalDate desde, LocalDate hasta);
}
