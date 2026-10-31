package com.constructora.obras.diario.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.dominio.EntradaDiario;
import java.util.List;
import java.util.UUID;

/**
 * 103 · Consultar el diario de una obra: admin, gerente y el maestro y el residente asignados. Si el usuario no ve la
 * obra o la ve solo como cliente, OBRA_NO_ENCONTRADA; si la ve con otro papel (por ejemplo almacenero), SIN_PERMISO.
 */
public interface ConsultarDiarioCasoUso {

    String SIN_PERMISO = "SIN_PERMISO";

    /** Entradas del rango, de la fecha más reciente a la más antigua. */
    List<EntradaDiario> listar(Consulta consulta, Usuario usuario);

    /** desde y hasta llegan tal cual de la consulta (AAAA-MM-DD, o null): el caso de uso aplica los valores por defecto. */
    record Consulta(UUID obraId, String desde, String hasta) {
    }
}
