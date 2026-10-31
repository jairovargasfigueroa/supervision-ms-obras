package com.constructora.obras.obra.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.EventoDominio;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.Comando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.ContratoComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.InsumoComando;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso.PartidaComando;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import com.constructora.obras.obra.aplicacion.servicio.RegistrarObraServicio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.ObraRegistrada;
import com.constructora.obras.obra.dominio.Zona;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarObraServicioTest {

    private static final Asignaciones ASIGNACIONES = new Asignaciones(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID());
    /** 2026-11-01 a las 12:00 en La Paz. */
    private static final Reloj RELOJ = () -> Instant.parse("2026-11-01T16:00:00Z");

    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final PublicadorEnMemoria publicador = new PublicadorEnMemoria();
    private final RegistrarObraServicio servicio = new RegistrarObraServicio(repositorio, publicador, RELOJ);

    @Test
    void guardaLaObraSinContratoYPublicaObraRegistrada() {
        ObraDetalle detalle = servicio.ejecutar(comando("357425.00"));

        Obra obra = detalle.obra();
        assertThat(obra.estado()).isEqualTo(EstadoObra.SIN_CONTRATO);
        assertThat(repositorio.obras).containsKey(obra.id());
        assertThat(obra.partidas()).singleElement().satisfies(partida -> {
            assertThat(partida.codigo()).isEqualTo("P-01");
            assertThat(partida.insumos()).singleElement()
                    .satisfies(insumo -> assertThat(insumo.cantidadPorUnidad()).isEqualByComparingTo("1.1"));
        });
        assertThat(detalle.indicadores().avancePct()).isEqualByComparingTo("0");
        assertThat(detalle.indicadores().plazoPct()).isEqualByComparingTo("0");

        assertThat(publicador.eventos).singleElement().isInstanceOfSatisfying(ObraRegistrada.class, evento -> {
            assertThat(evento.obraId()).isEqualTo(obra.id());
            assertThat(evento.correlacionId()).isEqualTo(obra.id());
            assertThat(evento.asignaciones()).isEqualTo(ASIGNACIONES);
            assertThat(evento.contrato().monto()).isEqualByComparingTo("357425.00");
            assertThat(evento.contrato().monto().scale()).isEqualTo(2);
            assertThat(evento.contrato().firmadoEn()).isEqualTo(LocalDate.of(2026, 10, 20));
        });
    }

    @Test
    void siUnaReglaFallaNoGuardaNiPublica() {
        assertThatThrownBy(() -> servicio.ejecutar(comando("1250000.00")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("PRESUPUESTO_NO_COINCIDE"));
        assertThat(repositorio.obras).isEmpty();
        assertThat(publicador.eventos).isEmpty();
    }

    private static Comando comando(String monto) {
        return new Comando("Edificio Las Palmas", "Av. Banzer 4to anillo", Zona.NORTE, LocalDate.of(2026, 11, 1),
                ASIGNACIONES,
                new ContratoComando(new BigDecimal(monto), 300, BigDecimal.TEN, BigDecimal.valueOf(7),
                        LocalDate.of(2026, 10, 20)),
                List.of(new PartidaComando("P-01", "Losa de hormigón armado", "m2", BigDecimal.valueOf(850),
                        new BigDecimal("420.50"), "nov-dic",
                        List.of(new InsumoComando("cemento", new BigDecimal("1.1"))))));
    }

    private static final class RepositorioEnMemoria implements ObraRepositorio {

        private final Map<UUID, Obra> obras = new LinkedHashMap<>();

        @Override
        public void guardar(Obra obra) {
            obras.put(obra.id(), obra);
        }

        @Override
        public Optional<Obra> buscarPorId(UUID id) {
            return Optional.ofNullable(obras.get(id));
        }

        @Override
        public List<Obra> listarTodas(FiltroObras filtro) {
            return List.copyOf(obras.values());
        }

        @Override
        public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
                boolean comoAlmacenero, boolean comoCliente, FiltroObras filtro) {
            return List.of();
        }
    }

    private static final class PublicadorEnMemoria implements PublicadorEventos {

        private final List<EventoDominio> eventos = new ArrayList<>();

        @Override
        public void publicar(EventoDominio evento) {
            eventos.add(evento);
        }
    }
}
