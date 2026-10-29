package com.constructora.obras.soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Datos de planillas escritos directo en la base: en la 006 no hay endpoints para crearlas (llegan con la 007), así
 * que las pruebas de avance y material previsto las insertan aquí. También cambia datos de la obra que la API todavía
 * no permite (estado y fecha de inicio).
 */
public final class PlanillasPrueba {

    private PlanillasPrueba() {
    }

    /**
     * Inserta una planilla con una línea por partida (cantidad × precio unitario de la partida). Los totales no
     * importan para la 006 y quedan en 0.
     *
     * @param periodo AAAA-MM
     * @return id de la planilla
     */
    public static UUID insertar(JdbcTemplate jdbc, UUID obraId, int numero, String periodo, String estado,
            Map<UUID, BigDecimal> cantidadPorPartida) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into planilla (id, obra_id, numero, periodo, estado, subtotal, descuento_anticipo, retencion,
                                      neto, version)
                values (?, ?, ?, ?, ?, 0, 0, 0, 0, 0)""",
                id, obraId, numero, LocalDate.parse(periodo + "-01"), estado);
        cantidadPorPartida.forEach((partidaId, cantidad) -> jdbc.update("""
                insert into linea_planilla (planilla_id, partida_id, cantidad_periodo, monto)
                select ?, p.id, ?, round(? * p.precio_unitario, 2) from partida p where p.id = ?""",
                id, cantidad, cantidad, partidaId));
        return id;
    }

    /** Id de la partida de la obra con ese código. */
    public static UUID partida(JdbcTemplate jdbc, UUID obraId, String codigo) {
        return jdbc.queryForObject("select id from partida where obra_id = ? and codigo = ?", UUID.class, obraId,
                codigo);
    }

    /** Cambia la fecha de inicio de la obra (para fijar el plazo transcurrido respecto de hoy). */
    public static void cambiarInicio(JdbcTemplate jdbc, UUID obraId, LocalDate fechaInicio) {
        jdbc.update("update obra set fecha_inicio = ? where id = ?", fechaInicio, obraId);
    }

    /** Cambia el estado de la obra (EN_EJECUCION llega con ContratoRegistrado; aquí se fija directo). */
    public static void cambiarEstado(JdbcTemplate jdbc, UUID obraId, String estado) {
        jdbc.update("update obra set estado = ? where id = ?", estado, obraId);
    }
}
