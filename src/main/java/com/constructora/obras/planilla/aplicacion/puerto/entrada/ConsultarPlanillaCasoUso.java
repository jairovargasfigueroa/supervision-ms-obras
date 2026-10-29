package com.constructora.obras.planilla.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.planilla.dominio.Planilla;
import java.util.List;
import java.util.UUID;

/** Leer planillas (007): los que ven la obra; el cliente (solo como cliente) no ve las que están en BORRADOR. */
public interface ConsultarPlanillaCasoUso {

    String PLANILLA_NO_ENCONTRADA = "PLANILLA_NO_ENCONTRADA";

    /** Planillas de la obra por número. Si no existe o no la ve: OBRA_NO_ENCONTRADA. */
    List<Planilla> listar(UUID obraId, Usuario usuario);

    /** Detalle. Si no existe, no ve su obra o es un BORRADOR y la ve solo como cliente: PLANILLA_NO_ENCONTRADA. */
    PlanillaDetalle obtener(UUID planillaId, Usuario usuario);
}
