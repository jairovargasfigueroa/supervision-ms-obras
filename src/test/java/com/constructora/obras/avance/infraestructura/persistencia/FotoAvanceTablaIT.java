package com.constructora.obras.avance.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.soporte.PruebaIntegracion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migración V9 (301, docs/datos.md): foto_avance agrega sin_chaleco y version_modelo_epp, que admiten null (foto no
 * analizada) como personas_detectadas y sin_casco, que ya venían de la V2.
 */
class FotoAvanceTablaIT extends PruebaIntegracion {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void losCuatroCamposDeLaDeteccionAdmitenNull() {
        List<Map<String, Object>> columnas = jdbc.queryForList("""
                select column_name, data_type, is_nullable, character_maximum_length
                from information_schema.columns
                where table_name = 'foto_avance'
                  and column_name in ('personas_detectadas', 'sin_casco', 'sin_chaleco', 'version_modelo_epp')
                order by column_name""");

        assertThat(columnas).extracting(c -> c.get("column_name"))
                .containsExactly("personas_detectadas", "sin_casco", "sin_chaleco", "version_modelo_epp");
        assertThat(columnas).extracting(c -> c.get("is_nullable")).containsOnly("YES");
        assertThat(columnas).extracting(c -> c.get("data_type"))
                .containsExactly("integer", "integer", "integer", "character varying");
        assertThat(columnas.get(3).get("character_maximum_length")).isEqualTo(40);
    }
}
