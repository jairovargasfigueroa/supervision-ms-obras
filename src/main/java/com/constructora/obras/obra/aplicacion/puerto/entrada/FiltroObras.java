package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.obra.dominio.EstadoObra;

/**
 * Filtros de GET /obras (006, criterio 2): estado exacto y búsqueda por nombre (contiene el texto, sin distinguir
 * mayúsculas). Un valor nulo no filtra; la búsqueda se recorta y, si queda vacía, no filtra.
 */
public record FiltroObras(EstadoObra estado, String busqueda) {

    public static final FiltroObras NINGUNO = new FiltroObras(null, null);

    public FiltroObras {
        busqueda = busqueda == null || busqueda.isBlank() ? null : busqueda.strip();
    }
}
