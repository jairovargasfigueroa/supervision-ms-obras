package com.constructora.obras.avance.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.avance.aplicacion.DoblesAvance.FotosEnMemoria;
import com.constructora.obras.avance.aplicacion.DoblesAvance.ObrasEnMemoria;
import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso;
import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso.Consulta;
import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso.FotoListada;
import com.constructora.obras.avance.aplicacion.servicio.ConsultarFotosServicio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.AlmacenArchivos.AlmacenNoDisponibleException;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.soporte.AlmacenArchivosEnMemoria;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarFotosServicioTest {

    private static final Paginacion POR_DEFECTO = Paginacion.de(null, null, null, null, ConsultarFotosCasoUso.ORDENES,
            ConsultarFotosCasoUso.ORDEN_POR_DEFECTO);

    private final Asignaciones asignaciones = DoblesAvance.asignaciones();
    private final Obra obra = DoblesAvance.obra(asignaciones);
    private final UUID partidaId = obra.partidas().getFirst().id();
    private final FotosEnMemoria fotos = new FotosEnMemoria();
    private final AlmacenArchivosEnMemoria almacen = new AlmacenArchivosEnMemoria();
    private final ConsultarFotosServicio servicio = new ConsultarFotosServicio(new ObrasEnMemoria().con(obra), fotos,
            almacen);

    private final Usuario residente = new Usuario(asignaciones.residenteId().toString(), Set.of("residente"));
    private final Usuario cliente = new Usuario(asignaciones.clienteId().toString(), Set.of("cliente"));

    @Test
    void deLaMasRecienteALaMasAntiguaConUrlDeDescargaDeTreintaMinutos() {
        FotoAvance vieja = foto("2026-11-10T12:00:00Z", false);
        FotoAvance nueva = foto("2026-11-15T12:00:00Z", false);

        Pagina<FotoListada> pagina = servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), residente);

        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(20);
        assertThat(pagina.items()).extracting(listada -> listada.foto().id()).containsExactly(nueva.id(), vieja.id());
        assertThat(pagina.items().getFirst().urlDescarga())
                .isEqualTo(AlmacenArchivosEnMemoria.BASE + nueva.claveArchivo() + "?metodo=GET&vigencia=1800");
        assertThat(almacen.firmas()).allSatisfy(firma -> {
            assertThat(firma.metodo()).isEqualTo("GET");
            assertThat(firma.vigencia()).isEqualTo(Duration.ofMinutes(30));
        });
    }

    @Test
    void elClienteSoloRecibeLasPublicadas() {
        foto("2026-11-10T12:00:00Z", false);
        FotoAvance publicada = foto("2026-11-11T12:00:00Z", true);

        Pagina<FotoListada> pagina = servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), cliente);

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.items()).extracting(listada -> listada.foto().id()).containsExactly(publicada.id());
        // Con otro rol que ve la obra, ve todas
        assertThat(servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), residente).total()).isEqualTo(2);
    }

    @Test
    void filtraPorPartida() {
        foto("2026-11-10T12:00:00Z", false);

        assertThat(servicio.listar(new Consulta(obra.id(), partidaId, POR_DEFECTO), residente).total()).isEqualTo(1);
        assertThat(servicio.listar(new Consulta(obra.id(), UUID.randomUUID(), POR_DEFECTO), residente).total())
                .isZero();
    }

    @Test
    void quienNoVeLaObraRecibeObraNoEncontrada() {
        Usuario otro = new Usuario(UUID.randomUUID().toString(), Set.of("residente"));

        assertThatThrownBy(() -> servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), otro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_ENCONTRADA"));
    }

    @Test
    void sinAlmacenamientoUnaListaVaciaRespondeYUnaConFotosNo() {
        almacen.noDisponible();

        assertThat(servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), residente).items()).isEmpty();

        foto("2026-11-10T12:00:00Z", false);
        assertThatThrownBy(() -> servicio.listar(new Consulta(obra.id(), null, POR_DEFECTO), residente))
                .isInstanceOf(AlmacenNoDisponibleException.class);
    }

    private FotoAvance foto(String registradoEn, boolean publicada) {
        UUID id = UUID.randomUUID();
        FotoAvance foto = new FotoAvance(id, obra.id(), partidaId, FotoAvance.claveArchivo(obra.id(), id), publicada,
                asignaciones.maestroId(), Instant.parse(registradoEn));
        fotos.guardar(foto);
        return foto;
    }
}
