package com.constructora.obras.diario.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.diario.dominio.Clima;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Migración V6 (103, docs/datos.md): una entrada por obra y día, y solo los cuatro valores de clima; y el adaptador
 * EntradaDiarioRepositorioJpa (guardar sin abortar la transacción cuando choca, lista por rango).
 */
class EntradaDiarioTablaIT extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 15);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntradaDiarioRepositorio repositorio;
    @Autowired
    private TransactionTemplate transaccion;

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

    @Test
    void guardarSiNoExisteDevuelveFalseSiChocaYLaTransaccionSigue() {
        UUID maestro = UUID.randomUUID();
        Instant ahora = Instant.parse("2026-11-15T23:10:00.123456Z");
        EntradaDiario primera = new EntradaDiario(UUID.randomUUID(), obra, DIA, Clima.LLUVIA_LEVE, "Texto del día",
                maestro, ahora);

        transaccion.executeWithoutResult(estado -> {
            assertThat(repositorio.guardarSiNoExiste(primera)).isTrue();
            // Mismo id con otra fecha, y otro id con la misma fecha: la base no guarda ninguna
            assertThat(repositorio.guardarSiNoExiste(new EntradaDiario(primera.id(), obra, DIA.plusDays(1),
                    Clima.SOLEADO, "Otro texto", maestro, ahora))).isFalse();
            assertThat(repositorio.guardarSiNoExiste(new EntradaDiario(UUID.randomUUID(), obra, DIA,
                    Clima.SOLEADO, "Otro texto", maestro, ahora))).isFalse();
            assertThat(repositorio.buscarPorId(primera.id())).contains(primera);
        });
        assertThat(repositorio.buscarPorObraYFecha(obra, DIA)).contains(primera);
    }

    @Test
    void listaElRangoIncluidoDeLaMasRecienteALaMasAntigua() throws Exception {
        for (int dia = 0; dia < 5; dia++) {
            insertar(obra, DIA.plusDays(dia), "SOLEADO");
        }
        insertar(ObrasPrueba.registrar(mvc, "Otra obra", Equipo.nuevo()), DIA.plusDays(2), "SOLEADO");

        assertThat(repositorio.listar(obra, DIA.plusDays(1), DIA.plusDays(3))).extracting(EntradaDiario::fecha)
                .containsExactly(DIA.plusDays(3), DIA.plusDays(2), DIA.plusDays(1));
    }

    private void insertar(UUID obraId, LocalDate fecha, String clima) {
        jdbc.update("""
                insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
                values (?, ?, ?, ?, 'Texto de la entrada', ?, now())""", UUID.randomUUID(), obraId, fecha, clima,
                UUID.randomUUID());
    }
}
