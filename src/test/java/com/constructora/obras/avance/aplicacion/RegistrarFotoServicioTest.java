package com.constructora.obras.avance.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.avance.aplicacion.DoblesAvance.FotosEnMemoria;
import com.constructora.obras.avance.aplicacion.DoblesAvance.ObrasEnMemoria;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso.Comando;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso.DeteccionRecibida;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso.Registro;
import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso.UrlSubida;
import com.constructora.obras.avance.aplicacion.servicio.RegistrarFotoServicio;
import com.constructora.obras.avance.dominio.DeteccionEpp;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.AlmacenArchivos.AlmacenNoDisponibleException;
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

class RegistrarFotoServicioTest {

    private static final Instant TOMADA_EN = Instant.parse("2026-11-15T15:20:00Z");

    private final Asignaciones asignaciones = DoblesAvance.asignaciones();
    private final Obra obra = DoblesAvance.obra(asignaciones);
    private final UUID partidaId = obra.partidas().getFirst().id();
    private final FotosEnMemoria fotos = new FotosEnMemoria();
    private final AlmacenArchivosEnMemoria almacen = new AlmacenArchivosEnMemoria();
    private final RegistrarFotoServicio servicio = new RegistrarFotoServicio(new ObrasEnMemoria().con(obra), fotos,
            almacen);

    private final Usuario maestro = new Usuario(asignaciones.maestroId().toString(), Set.of("maestro"));
    private final Usuario residente = new Usuario(asignaciones.residenteId().toString(), Set.of("residente"));

    @Test
    void laUrlDeSubidaEsPutJpegDeDiezMinutosConLaClaveDeLaFoto() {
        UUID fotoId = UUID.randomUUID();

        UrlSubida subida = servicio.prepararSubida(obra.id(), fotoId, maestro);

        String clave = "fotos/" + obra.id() + "/" + fotoId + ".jpg";
        assertThat(subida.claveArchivo()).isEqualTo(clave);
        assertThat(subida.url()).startsWith(AlmacenArchivosEnMemoria.BASE + clave);
        assertThat(almacen.firmas()).singleElement().isEqualTo(
                new AlmacenArchivosEnMemoria.Firma(clave, "PUT", "image/jpeg", Duration.ofMinutes(10)));
    }

    @Test
    void registraConTomadaEnYElUsuarioYSiSeRepiteDevuelveLaMisma() {
        UUID fotoId = UUID.randomUUID();
        Comando comando = new Comando(obra.id(), fotoId, partidaId, FotoAvance.claveArchivo(obra.id(), fotoId),
                TOMADA_EN, null);

        Registro primero = servicio.registrar(comando, maestro);
        Registro repetido = servicio.registrar(comando, maestro);

        assertThat(primero.creada()).isTrue();
        assertThat(primero.foto().registradoEn()).isEqualTo(TOMADA_EN);
        assertThat(primero.foto().registradoPor()).isEqualTo(asignaciones.maestroId());
        assertThat(repetido.creada()).isFalse();
        assertThat(repetido.foto()).isEqualTo(primero.foto());
        assertThat(fotos.fotos).hasSize(1);
        assertThat(primero.foto().deteccion()).isNull();
        assertThat(primero.avisos()).isEmpty();
    }

    @Test
    void registraLaDeteccionValidaSinAvisos() {
        UUID fotoId = UUID.randomUUID();

        Registro registro = servicio.registrar(comando(fotoId, new DeteccionRecibida(4, 1, 2, "epp-1.0.0")),
                maestro);

        assertThat(registro.creada()).isTrue();
        assertThat(registro.avisos()).isEmpty();
        assertThat(registro.foto().deteccion()).isEqualTo(new DeteccionEpp(4, 1, 2, "epp-1.0.0"));
        assertThat(fotos.fotos.get(fotoId).deteccion()).isEqualTo(new DeteccionEpp(4, 1, 2, "epp-1.0.0"));
    }

    @Test
    void unaDeteccionInvalidaSeDescartaYLaFotoSeRegistraIgual() {
        UUID fotoId = UUID.randomUUID();

        Registro registro = servicio.registrar(comando(fotoId, new DeteccionRecibida(4, 5, 2, "epp-1.0.0")),
                maestro);

        assertThat(registro.creada()).isTrue();
        assertThat(registro.avisos()).containsExactly("DETECCION_DESCARTADA");
        assertThat(registro.foto().deteccion()).isNull();
        assertThat(fotos.fotos.get(fotoId).deteccion()).isNull();
    }

    @Test
    void unaDeteccionConCamposQueFaltanTambienSeDescarta() {
        Registro registro = servicio.registrar(
                comando(UUID.randomUUID(), new DeteccionRecibida(4, null, 2, "epp-1.0.0")), maestro);

        assertThat(registro.avisos()).containsExactly("DETECCION_DESCARTADA");
        assertThat(registro.foto().deteccion()).isNull();
    }

    @Test
    void elRegistroRepetidoNoCambiaLaDeteccionNiDaAvisos() {
        UUID fotoId = UUID.randomUUID();
        servicio.registrar(comando(fotoId, new DeteccionRecibida(4, 1, 2, "epp-1.0.0")), maestro);

        Registro conOtra = servicio.registrar(comando(fotoId, new DeteccionRecibida(3, 0, 0, "epp-2.0.0")), maestro);
        Registro conInvalida = servicio.registrar(comando(fotoId, new DeteccionRecibida(1, 9, 9, "")), maestro);
        Registro sinDeteccion = servicio.registrar(comando(fotoId, null), maestro);

        for (Registro repetido : new Registro[] {conOtra, conInvalida, sinDeteccion}) {
            assertThat(repetido.creada()).isFalse();
            assertThat(repetido.avisos()).isEmpty();
            assertThat(repetido.foto().deteccion()).isEqualTo(new DeteccionEpp(4, 1, 2, "epp-1.0.0"));
        }
        assertThat(fotos.fotos.get(fotoId).deteccion()).isEqualTo(new DeteccionEpp(4, 1, 2, "epp-1.0.0"));
    }

    @Test
    void conPartidaAjenaYDeteccionInvalidaGanaElErrorDeLaPartida() {
        UUID fotoId = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.registrar(new Comando(obra.id(), fotoId, UUID.randomUUID(),
                FotoAvance.claveArchivo(obra.id(), fotoId), TOMADA_EN, new DeteccionRecibida(1, 2, 0, "x")), maestro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("PARTIDA_NO_ES_DE_LA_OBRA"));
        assertThat(fotos.fotos).isEmpty();
    }

    private Comando comando(UUID fotoId, DeteccionRecibida deteccion) {
        return new Comando(obra.id(), fotoId, partidaId, FotoAvance.claveArchivo(obra.id(), fotoId), TOMADA_EN,
                deteccion);
    }

    @Test
    void elResidenteAsignadoTambienRegistra() {
        UUID fotoId = UUID.randomUUID();
        Registro registro = servicio.registrar(new Comando(obra.id(), fotoId, partidaId,
                FotoAvance.claveArchivo(obra.id(), fotoId), TOMADA_EN, null), residente);

        assertThat(registro.foto().registradoPor()).isEqualTo(asignaciones.residenteId());
    }

    @Test
    void quienNoVeLaObraRecibeObraNoEncontradaYQuienLaVeConOtroPapelSinPermiso() {
        Usuario otroMaestro = new Usuario(UUID.randomUUID().toString(), Set.of("maestro"));
        assertThatThrownBy(() -> servicio.prepararSubida(obra.id(), UUID.randomUUID(), otroMaestro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_ENCONTRADA"));
        assertThatThrownBy(() -> servicio.prepararSubida(UUID.randomUUID(), UUID.randomUUID(), maestro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("OBRA_NO_ENCONTRADA"));

        // Ve la obra como almacenero, pero no es su maestro ni su residente
        Usuario almaceneroYMaestro = new Usuario(asignaciones.almaceneroId().toString(),
                Set.of("almacenero", "maestro"));
        assertThatThrownBy(() -> servicio.prepararSubida(obra.id(), UUID.randomUUID(), almaceneroYMaestro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("SIN_PERMISO"));
        assertThat(almacen.firmas()).isEmpty();
    }

    @Test
    void unIdQueYaExisteEnOtraObraNoSeAsocia() {
        UUID fotoId = UUID.randomUUID();
        UUID otraObra = UUID.randomUUID();
        fotos.guardar(new FotoAvance(fotoId, otraObra, UUID.randomUUID(), FotoAvance.claveArchivo(otraObra, fotoId),
                false, UUID.randomUUID(), TOMADA_EN, null));

        assertThatThrownBy(() -> servicio.registrar(new Comando(obra.id(), fotoId, partidaId,
                FotoAvance.claveArchivo(obra.id(), fotoId), TOMADA_EN, null), maestro))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("CLAVE_ARCHIVO_INVALIDA"));
    }

    @Test
    void siNoHayAlmacenamientoLaUrlNoSePuedeFirmar() {
        almacen.noDisponible();

        assertThatThrownBy(() -> servicio.prepararSubida(obra.id(), UUID.randomUUID(), maestro))
                .isInstanceOf(AlmacenNoDisponibleException.class);
    }
}
