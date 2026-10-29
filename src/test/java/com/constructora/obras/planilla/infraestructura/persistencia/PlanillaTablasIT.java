package com.constructora.obras.planilla.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import com.constructora.obras.planilla.dominio.PlanillaCorta;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Migraciones V3 (docs/datos.md, planilla y linea_planilla) y V4 (planilla_foto, 007) y la lectura de la 006
 * (PlanillasObraRepositorioJpa).
 */
class PlanillaTablasIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlanillasObraRepositorio planillas;

    private UUID obra;
    private UUID partida;

    @BeforeEach
    void obra() throws Exception {
        obra = ObrasPrueba.registrar(mvc, "Obra con planillas", Equipo.nuevo());
        partida = PlanillasPrueba.partida(jdbc, obra, "P-01");
    }

    @Test
    void unaPlanillaPorPeriodoYNumeroPorObra() {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-09", "BORRADOR", Map.of());

        assertThatThrownBy(() -> PlanillasPrueba.insertar(jdbc, obra, 2, "2026-09", "BORRADOR", Map.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> PlanillasPrueba.insertar(jdbc, obra, 1, "2026-10", "BORRADOR", Map.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elEstadoYElPeriodoSeValidanEnLaBase() {
        assertThatThrownBy(() -> PlanillasPrueba.insertar(jdbc, obra, 1, "2026-09", "ANULADA", Map.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into planilla (id, obra_id, numero, periodo, estado, subtotal, descuento_anticipo, retencion,
                                      neto, version)
                values (?, ?, 1, date '2026-09-15', 'BORRADOR', 0, 0, 0, 0, 0)""", UUID.randomUUID(), obra))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laLecturaSumaSoloAprobadasYDevuelveLaUltimaYElPeriodo() {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-08", "APROBADA", Map.of(partida, new BigDecimal("1.25")));
        PlanillasPrueba.insertar(jdbc, obra, 2, "2026-09", "APROBADA", Map.of(partida, new BigDecimal("2")));
        PlanillasPrueba.insertar(jdbc, obra, 3, "2026-10", "OBSERVADA", Map.of(partida, new BigDecimal("4")));

        assertThat(planillas.ejecutadaAcumulada(List.of(obra)).get(obra).get(partida))
                .isEqualByComparingTo("3.25");
        assertThat(planillas.ultimas(List.of(obra)))
                .containsEntry(obra, new PlanillaCorta(Periodo.de("2026-10"), EstadoPlanilla.OBSERVADA));
        assertThat(planillas.cantidadesDelPeriodo(obra, Periodo.de("2026-10")))
                .hasValueSatisfying(cantidades -> assertThat(cantidades.get(partida)).isEqualByComparingTo("4"));
        assertThat(planillas.cantidadesDelPeriodo(obra, Periodo.de("2026-11"))).isEmpty();
        assertThat(planillas.ejecutadaAcumulada(List.of())).isEmpty();
        assertThat(planillas.ultimas(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void planillaFotoUneUnaPlanillaConFotosQueExisten() {
        UUID planilla = PlanillasPrueba.insertar(jdbc, obra, 1, "2026-10", "BORRADOR", Map.of());
        UUID foto = PlanillasPrueba.insertarFoto(jdbc, obra, partida, Instant.parse("2026-10-15T14:00:00Z"));

        PlanillasPrueba.adjuntarFoto(jdbc, planilla, foto);

        assertThat(jdbc.queryForObject("select count(*) from planilla_foto where planilla_id = ?", Integer.class,
                planilla)).isEqualTo(1);
        assertThatThrownBy(() -> PlanillasPrueba.adjuntarFoto(jdbc, planilla, foto))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> PlanillasPrueba.adjuntarFoto(jdbc, planilla, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> PlanillasPrueba.adjuntarFoto(jdbc, UUID.randomUUID(), foto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaPlanillaSinLineasExisteEnSuPeriodo() {
        PlanillasPrueba.insertar(jdbc, obra, 1, "2026-10", "BORRADOR", Map.of());

        assertThat(planillas.cantidadesDelPeriodo(obra, Periodo.de("2026-10"))).hasValue(Map.of());
    }
}
