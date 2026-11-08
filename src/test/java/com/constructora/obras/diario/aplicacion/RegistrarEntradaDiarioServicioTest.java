package com.constructora.obras.diario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.aplicacion.DoblesDiario.EntradasEnMemoria;
import com.constructora.obras.diario.aplicacion.DoblesDiario.ObrasEnMemoria;
import com.constructora.obras.diario.aplicacion.puerto.entrada.RegistrarEntradaDiarioCasoUso.Comando;
import com.constructora.obras.diario.aplicacion.puerto.entrada.RegistrarEntradaDiarioCasoUso.Registro;
import com.constructora.obras.diario.aplicacion.servicio.RegistrarEntradaDiarioServicio;
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

class RegistrarEntradaDiarioServicioTest {

    /** 19:10 del 15/11 en La Paz. */
    private static final Instant AHORA = Instant.parse("2026-11-15T23:10:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 15);
    private static final String TEXTO = "Se vació la losa del 2do piso. Lluvia por la tarde.";

    private final Asignaciones asignaciones = DoblesDiario.asignaciones();
    private final Obra obra = DoblesDiario.obra(asignaciones, EstadoObra.EN_EJECUCION);
    private final ObrasEnMemoria obras = new ObrasEnMemoria().con(obra);
    private final EntradasEnMemoria entradas = new EntradasEnMemoria();
    private final RegistrarEntradaDiarioServicio servicio = new RegistrarEntradaDiarioServicio(obras, entradas,
            () -> AHORA);

    private final Usuario maestro = new Usuario(asignaciones.maestroId().toString(), Set.of("maestro"));

    @Test
    void registraConElMaestroYLaHoraDelServidorYSiSeRepiteElIdDevuelveLaGuardada() {
        UUID id = UUID.randomUUID();

        Registro primero = servicio.registrar(comando(id, HOY, "LLUVIA_LEVE", "  " + TEXTO + " "), maestro);
        // Mismo id con otro cuerpo: se devuelve la guardada sin volver a validar fecha ni día
        Registro repetido = servicio.registrar(comando(id, HOY.minusDays(1), "SOLEADO", "Otro texto del día"),
                maestro);

        assertThat(primero.creada()).isTrue();
        assertThat(primero.entrada()).isEqualTo(new EntradaDiario(id, obra.id(), HOY, Clima.LLUVIA_LEVE, TEXTO,
                asignaciones.maestroId(), AHORA));
        assertThat(repetido.creada()).isFalse();
        assertThat(repetido.entrada()).isEqualTo(primero.entrada());
        assertThat(entradas.entradas).hasSize(1);
    }

    @Test
    void otraEntradaDelMismoDiaEsDiaYaRegistrado() {
        servicio.registrar(comando(UUID.randomUUID(), HOY, "SOLEADO", TEXTO), maestro);

        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "NUBLADO", TEXTO), maestro),
                "DIARIO_DIA_YA_REGISTRADO");
        assertThat(entradas.entradas).hasSize(1);
    }

    @Test
    void siOtraPeticionGuardaElMismoDiaALaVezLaBaseLaRechazaYEsDiaYaRegistrado() {
        entradas.alGuardar = new EntradaDiario(UUID.randomUUID(), obra.id(), HOY, Clima.SOLEADO, TEXTO,
                asignaciones.maestroId(), AHORA);

        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "NUBLADO", TEXTO), maestro),
                "DIARIO_DIA_YA_REGISTRADO");
    }

    @Test
    void siOtraPeticionGuardaElMismoIdALaVezSeDevuelveLaGuardada() {
        UUID id = UUID.randomUUID();
        EntradaDiario otra = new EntradaDiario(id, obra.id(), HOY, Clima.SOLEADO, TEXTO, asignaciones.maestroId(),
                AHORA.minusSeconds(1));
        entradas.alGuardar = otra;

        Registro registro = servicio.registrar(comando(id, HOY, "SOLEADO", TEXTO), maestro);

        assertThat(registro.creada()).isFalse();
        assertThat(registro.entrada()).isEqualTo(otra);
    }

    @Test
    void unIdQueYaExisteEnOtraObraEsDiaYaRegistrado() {
        UUID id = UUID.randomUUID();
        entradas.agregar(new EntradaDiario(id, UUID.randomUUID(), HOY, Clima.SOLEADO, TEXTO, UUID.randomUUID(),
                AHORA));

        assertCodigo(() -> servicio.registrar(comando(id, HOY, "SOLEADO", TEXTO), maestro),
                "DIARIO_DIA_YA_REGISTRADO");
    }

    @Test
    void quienNoVeLaObraRecibeObraNoEncontradaYQuienLaVeConOtroPapelSinPermiso() {
        Usuario otroMaestro = new Usuario(UUID.randomUUID().toString(), Set.of("maestro"));
        Usuario residente = new Usuario(asignaciones.residenteId().toString(), Set.of("residente"));
        // Con los roles maestro y residente, asignado solo como residente
        Usuario residenteConRolMaestro = new Usuario(asignaciones.residenteId().toString(),
                Set.of("maestro", "residente"));
        Usuario admin = new Usuario(UUID.randomUUID().toString(), Set.of("admin"));

        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "SOLEADO", TEXTO), otroMaestro),
                "OBRA_NO_ENCONTRADA");
        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "SOLEADO", TEXTO), residente),
                "SIN_PERMISO");
        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "SOLEADO", TEXTO),
                residenteConRolMaestro), "SIN_PERMISO");
        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "SOLEADO", TEXTO), admin),
                "SIN_PERMISO");
        assertCodigo(() -> servicio.registrar(new Comando(UUID.randomUUID(), UUID.randomUUID(), HOY, "SOLEADO",
                TEXTO), maestro), "OBRA_NO_ENCONTRADA");
    }

    @Test
    void elFormatoSeValidaAntesQueLaObra() {
        Usuario otroMaestro = new Usuario(UUID.randomUUID().toString(), Set.of("maestro"));

        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY, "GRANIZO", TEXTO), otroMaestro),
                "DATOS_INVALIDOS");
    }

    @Test
    void obraNoEnEjecucionYFechaInvalida() {
        Obra sinContrato = DoblesDiario.obra(asignaciones, EstadoObra.SIN_CONTRATO);
        obras.con(sinContrato);

        assertCodigo(() -> servicio.registrar(new Comando(sinContrato.id(), UUID.randomUUID(), HOY, "SOLEADO",
                TEXTO), maestro), "OBRA_NO_EN_EJECUCION");
        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), HOY.plusDays(1), "SOLEADO", TEXTO),
                maestro), "FECHA_DIARIO_INVALIDA");
        assertCodigo(() -> servicio.registrar(comando(UUID.randomUUID(), DoblesDiario.INICIO.minusDays(1),
                "SOLEADO", TEXTO), maestro), "FECHA_DIARIO_INVALIDA");
        assertThat(entradas.entradas).isEmpty();
    }

    private Comando comando(UUID id, LocalDate fecha, String clima, String texto) {
        return new Comando(obra.id(), id, fecha, clima, texto);
    }

    private static void assertCodigo(Executable accion, String codigo) {
        assertThatThrownBy(accion::execute).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo(codigo));
    }
}
