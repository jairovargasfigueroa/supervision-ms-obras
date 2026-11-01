package com.constructora.obras.planilla.infraestructura.persistencia;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso.Filtro;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso.PlanillaEstado;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillasEstadoRepositorio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 501 · Adaptador de PlanillasEstadoRepositorio con SQL de lectura (planilla con su obra). El SQL es constante: cada
 * filtro opcional agrega un fragmento fijo y los valores van como parámetros. Corre en la transacción del caso de uso.
 */
@Repository
public class PlanillasEstadoRepositorioJdbc implements PlanillasEstadoRepositorio {

    private final NamedParameterJdbcTemplate jdbc;

    public PlanillasEstadoRepositorioJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlanillaEstado> buscar(Filtro filtro, int maximo) {
        StringBuilder sql = new StringBuilder("""
                select p.id, p.obra_id, o.nombre, p.numero, p.periodo, p.estado, p.neto, p.observacion,
                       p.presentada_en, p.cobro_id
                from planilla p join obra o on o.id = p.obra_id
                where p.estado in (:estados)""");
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("estados", filtro.estados().stream().map(Enum::name).toList())
                .addValue("maximo", maximo);
        if (filtro.obraId() != null) {
            sql.append(" and p.obra_id = :obraId");
            parametros.addValue("obraId", filtro.obraId());
        }
        if (filtro.desdePeriodo() != null) {
            sql.append(" and p.periodo >= :desde");
            parametros.addValue("desde", filtro.desdePeriodo().primerDia());
        }
        sql.append(" order by p.periodo desc, o.nombre, p.numero limit :maximo");
        return jdbc.query(sql.toString(), parametros, (fila, numero) -> {
            Timestamp presentada = fila.getTimestamp("presentada_en");
            return new PlanillaEstado(fila.getObject("id", UUID.class), fila.getObject("obra_id", UUID.class),
                    fila.getString("nombre"), fila.getInt("numero"),
                    Periodo.de(fila.getDate("periodo").toLocalDate()),
                    EstadoPlanilla.valueOf(fila.getString("estado")), fila.getBigDecimal("neto"),
                    fila.getString("observacion"), presentada == null ? null : presentada.toInstant(),
                    fila.getObject("cobro_id", UUID.class));
        });
    }
}
