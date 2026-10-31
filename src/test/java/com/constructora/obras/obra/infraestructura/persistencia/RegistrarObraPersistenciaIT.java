package com.constructora.obras.obra.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.Comando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.ContratoComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.InsumoComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.PartidaComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Zona;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Tarea 3: el caso de uso guarda la obra con partidas e insumos y la vuelve a leer igual. */
class RegistrarObraPersistenciaIT extends PruebaIntegracion {

    @Autowired
    private RegistrarObraCasoUso registrar;
    @Autowired
    private ObraRepositorio repositorio;
    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void guardaYLeeLaObraConSusPartidasEInsumos() {
        Asignaciones asignaciones = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        UUID id = registrar.ejecutar(new Comando("Edificio Las Palmas", "Av. Banzer 4to anillo", Zona.ESTE,
                LocalDate.of(2026, 11, 1), asignaciones,
                new ContratoComando(new BigDecimal("400"), 300, BigDecimal.TEN, BigDecimal.valueOf(7),
                        LocalDate.of(2026, 10, 20)),
                List.of(new PartidaComando("P-02", "Muro", "m2", BigDecimal.ONE, new BigDecimal("100"), null, List.of()),
                        new PartidaComando("P-01", "Losa", "m2", new BigDecimal("2.5"), new BigDecimal("120"), "nov",
                                List.of(new InsumoComando("fierro", new BigDecimal("4")),
                                        new InsumoComando("cemento", new BigDecimal("1.1")))))))
                .obra().id();

        Obra leida = transaccion.execute(estado -> repositorio.buscarPorId(id).orElseThrow());

        assertThat(leida.estado()).isEqualTo(EstadoObra.SIN_CONTRATO);
        assertThat(leida.zona()).isEqualTo(Zona.ESTE);
        assertThat(leida.asignaciones()).isEqualTo(asignaciones);
        assertThat(leida.contrato().contratoId()).isNull();
        assertThat(leida.contrato().monto().valor()).isEqualByComparingTo("400.00");
        assertThat(leida.contrato().anticipoPct()).isEqualByComparingTo("10");
        assertThat(leida.partidas()).extracting(partida -> partida.codigo()).containsExactly("P-01", "P-02");
        assertThat(leida.partidas().getFirst().insumos()).extracting(insumo -> insumo.materialId())
                .containsExactly("cemento", "fierro");
        assertThat(leida.partidas().getFirst().cantidadContratada()).isEqualByComparingTo("2.5");
        assertThat(leida.partidas().get(1).meses()).isNull();
    }
}
