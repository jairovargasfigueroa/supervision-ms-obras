package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import java.util.List;
import java.util.UUID;

/** Consultar obras con la visibilidad por rol (convenciones, «Autenticación y permisos»). */
public interface ConsultarObraCasoUso {

    String OBRA_NO_ENCONTRADA = "OBRA_NO_ENCONTRADA";

    /** Detalle de una obra que el usuario puede ver; si no existe o no la ve: OBRA_NO_ENCONTRADA. */
    ObraDetalle obtener(UUID obraId, Usuario usuario);

    /** Obras que el usuario puede ver, por nombre. */
    List<ObraDetalle> listar(Usuario usuario);
}
