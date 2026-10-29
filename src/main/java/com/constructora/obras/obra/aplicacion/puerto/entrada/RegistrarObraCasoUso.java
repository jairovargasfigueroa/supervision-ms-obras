package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.obra.dominio.Asignaciones;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 002 · Registrar obra con su contrato y su presupuesto por partidas (rol admin, lo valida el controlador). */
public interface RegistrarObraCasoUso {

    ObraDetalle ejecutar(Comando comando);

    record Comando(String nombre, String ubicacion, LocalDate fechaInicio, Asignaciones asignaciones,
            ContratoComando contrato, List<PartidaComando> partidas) {
    }

    record ContratoComando(BigDecimal monto, int plazoDias, BigDecimal anticipoPct, BigDecimal retencionPct,
            LocalDate firmadoEn) {
    }

    record PartidaComando(String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
            BigDecimal precioUnitario, String meses, List<InsumoComando> insumos) {
    }

    record InsumoComando(String materialId, BigDecimal cantidadPorUnidad) {
    }
}
