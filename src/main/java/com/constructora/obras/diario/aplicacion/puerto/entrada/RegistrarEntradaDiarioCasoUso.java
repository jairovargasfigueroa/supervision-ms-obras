package com.constructora.obras.diario.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.dominio.EntradaDiario;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 103 · Registrar entrada del diario de obra. Solo el maestro asignado: si el usuario no ve la obra,
 * OBRA_NO_ENCONTRADA; si la ve con otro papel, SIN_PERMISO. El id lo crea el celular (idempotencia).
 */
public interface RegistrarEntradaDiarioCasoUso {

    String SIN_PERMISO = "SIN_PERMISO";

    /** Registra la entrada; si el id ya existe en la obra devuelve la guardada (creada = false). */
    Registro registrar(Comando comando, Usuario usuario);

    /** clima y texto llegan tal cual del pedido: el caso de uso valida su formato. */
    record Comando(UUID obraId, UUID id, LocalDate fecha, String clima, String texto) {
    }

    record Registro(EntradaDiario entrada, boolean creada) {
    }
}
