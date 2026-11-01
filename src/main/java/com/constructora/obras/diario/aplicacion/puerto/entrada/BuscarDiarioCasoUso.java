package com.constructora.obras.diario.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.dominio.EntradaDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 501 · Buscar en el diario de una obra (herramienta MCP buscar_diario): rango de fechas ya validado por quien llama,
 * texto opcional (contiene, sin distinguir mayúsculas; %, _ y la barra invertida son texto literal) y un máximo de
 * entradas. Mismos permisos que ConsultarDiarioCasoUso: si el usuario no ve la obra, OBRA_NO_ENCONTRADA.
 */
public interface BuscarDiarioCasoUso {

    Resultado buscar(Busqueda busqueda, Usuario usuario);

    /** texto: null no filtra. limite: entradas máximas que se devuelven. */
    record Busqueda(UUID obraId, LocalDate desde, LocalDate hasta, String texto, int limite) {

        public Busqueda {
            Objects.requireNonNull(obraId, "obraId");
            Objects.requireNonNull(desde, "desde");
            Objects.requireNonNull(hasta, "hasta");
        }
    }

    /** entradas: de la más reciente a la más antigua, hasta el límite. total: las que cumplen el filtro. */
    record Resultado(List<EntradaDiario> entradas, long total) {

        public Resultado {
            entradas = List.copyOf(entradas);
        }
    }
}
