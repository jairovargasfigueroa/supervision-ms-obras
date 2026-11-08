package com.constructora.obras.diario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.aplicacion.DoblesDiario.EntradasEnMemoria;
import com.constructora.obras.diario.aplicacion.DoblesDiario.ObrasEnMemoria;
import com.constructora.obras.diario.aplicacion.puerto.entrada.ConsultarDiarioCasoUso.Consulta;
import com.constructora.obras.diario.aplicacion.servicio.ConsultarDiarioServicio;
import com.constructora.obras.diario.dominio.Clima;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ConsultarDiarioServicioTest {

    /** 23:30 del 15/12 en La Paz: en UTC ya es el 16. */
    private static final Instant AHORA = Instant.parse("2026-12-16T03:30:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 12, 15);

    private final Asignaciones asignaciones = DoblesDiario.asignaciones();
    private final Obra obra = DoblesDiario.obra(asignaciones, EstadoObra.EN_EJECUCION);
    private final EntradasEnMemoria entradas = new EntradasEnMemoria();
    private final ConsultarDiarioServicio servicio = new ConsultarDiarioServicio(new ObrasEnMemoria().con(obra),
            entradas, () -> AHORA);

    @Test
    void sinFechasDevuelveLosUltimosTreintaDiasDeLaPazDeLaMasRecienteALaMasAntigua() {
        for (int dias : new int[] {30, 29, 10, 0}) {
            entrada(HOY.minusDays(dias));
        }
        entrada(HOY.plusDays(1)); // solo puede estar si se cargó directo: igual queda fuera del rango

        assertThat(servicio.listar(new Consulta(obra.id(), null, null), usuario("maestro", asignaciones.maestroId())))
                .extracting(EntradaDiario::fecha)
                .containsExactly(HOY, HOY.minusDays(10), HOY.minusDays(29));
    }

    @Test
    void lasVenAdminGerenteYMaestroYResidenteAsignados() {
        entrada(HOY);
        for (Usuario usuario : new Usuario[] {usuario("admin", UUID.randomUUID()),
                usuario("gerente", UUID.randomUUID()), usuario("maestro", asignaciones.maestroId()),
                usuario("residente", asignaciones.residenteId())}) {
            assertThat(servicio.listar(new Consulta(obra.id(), null, null), usuario)).hasSize(1);
        }
    }

    @Test
    void elClienteYQuienNoVeLaObraRecibenObraNoEncontradaYElAlmaceneroSinPermiso() {
        assertCodigo(() -> servicio.listar(new Consulta(obra.id(), null, null),
                usuario("cliente", asignaciones.clienteId())), "OBRA_NO_ENCONTRADA");
        assertCodigo(() -> servicio.listar(new Consulta(obra.id(), null, null),
                usuario("maestro", UUID.randomUUID())), "OBRA_NO_ENCONTRADA");
        assertCodigo(() -> servicio.listar(new Consulta(UUID.randomUUID(), null, null),
                usuario("admin", UUID.randomUUID())), "OBRA_NO_ENCONTRADA");
        assertCodigo(() -> servicio.listar(new Consulta(obra.id(), null, null),
                usuario("almacenero", asignaciones.almaceneroId())), "SIN_PERMISO");
    }

    @Test
    void elRangoSeValidaAntesQueLaObra() {
        assertCodigo(() -> servicio.listar(new Consulta(UUID.randomUUID(), "2025-01-01", "2026-12-15"),
                usuario("admin", UUID.randomUUID())), "RANGO_INVALIDO");
    }

    private void entrada(LocalDate fecha) {
        entradas.agregar(new EntradaDiario(UUID.randomUUID(), obra.id(), fecha, Clima.NUBLADO, "Texto del día",
                asignaciones.maestroId(), AHORA));
    }

    private static Usuario usuario(String rol, UUID id) {
        return new Usuario(id.toString(), Set.of(rol));
    }

    private static void assertCodigo(Executable accion, String codigo) {
        assertThatThrownBy(accion::execute).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo(codigo));
    }
}
