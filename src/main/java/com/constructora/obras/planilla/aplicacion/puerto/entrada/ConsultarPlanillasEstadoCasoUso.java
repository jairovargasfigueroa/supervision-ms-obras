package com.constructora.obras.planilla.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 501 · Planillas de varias obras por estado (herramienta MCP planillas_estado), de periodo más reciente a más
 * antiguo (luego nombre de la obra y número). Solo para quien ve todas las obras (admin, gerente, agente_ia); si se
 * indica una obra que no existe, OBRA_NO_ENCONTRADA.
 */
public interface ConsultarPlanillasEstadoCasoUso {

    Resultado consultar(Filtro filtro, Usuario usuario);

    /**
     * @param obraId       null: todas las obras
     * @param estados      no vacío
     * @param desdePeriodo null no filtra; si no, periodo mayor o igual
     * @param limite       planillas máximas que se devuelven
     */
    record Filtro(UUID obraId, Set<EstadoPlanilla> estados, Periodo desdePeriodo, int limite) {

        public Filtro {
            estados = Set.copyOf(estados);
            if (estados.isEmpty()) {
                throw new IllegalArgumentException("Falta al menos un estado");
            }
        }
    }

    /** hayMas: había más planillas que el límite. */
    record Resultado(List<PlanillaEstado> planillas, boolean hayMas) {

        public Resultado {
            planillas = List.copyOf(planillas);
        }
    }

    /** Lo que se lee de cada planilla, con el nombre de su obra. presentadaEn: null si no se sabe (anterior a la 501). */
    record PlanillaEstado(UUID planillaId, UUID obraId, String obraNombre, int numero, Periodo periodo,
            EstadoPlanilla estado, BigDecimal neto, String observacion, Instant presentadaEn, UUID cobroId) {

        public PlanillaEstado {
            Objects.requireNonNull(planillaId, "planillaId");
            Objects.requireNonNull(obraId, "obraId");
            Objects.requireNonNull(estado, "estado");
        }
    }
}
