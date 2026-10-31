package com.constructora.obras.diario.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Migración V6 (103, docs/datos.md): una entrada por obra y día, y solo los cuatro valores de clima. */
class EntradaDiarioTablaIT extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 15);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID obra;

    @BeforeEach
    void obra() throws Exception {
        obra = ObrasPrueba.registrar(mvc, "Obra con diario", Equipo.nuevo());
    }

    @Test
    void unaEntradaPorObraYDia() {
        insertar(obra, DIA, "SOLEADO");

        assertThatThrownBy(() -> insertar(obra, DIA, "NUBLADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertar(obra, DIA.plusDays(1), "NUBLADO")).doesNotThrowAnyException();
    }

    @Test
    void elClimaSeValidaEnLaBase() {
        String[] climas = {"SOLEADO", "NUBLADO", "LLUVIA_LEVE", "LLUVIA_FUERTE"};
        for (int i = 0; i < climas.length; i++) {
            insertar(obra, DIA.plusDays(i), climas[i]);
        }
        assertThatThrownBy(() -> insertar(obra, DIA.minusDays(1), "NEVADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertar(UUID obraId, LocalDate fecha, String clima) {
        jdbc.update("""
                insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
                values (?, ?, ?, ?, 'Texto de la entrada', ?, now())""", UUID.randomUUID(), obraId, fecha, clima,
                UUID.randomUUID());
    }
}
