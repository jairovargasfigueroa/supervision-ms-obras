package com.constructora.obras.avance.aplicacion.servicio;

import com.constructora.obras.avance.aplicacion.puerto.entrada.RegistrarFotoCasoUso;
import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.AlmacenArchivos;
import com.constructora.obras.compartido.dominio.AlmacenArchivos.UrlFirmada;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** URL de subida y registro idempotente de fotos de avance (005, docs/specs/005-foto-avance/diseno.md). */
@Service
public class RegistrarFotoServicio implements RegistrarFotoCasoUso {

    /** Vigencia de la URL de subida (diseño de la 005). */
    static final Duration VIGENCIA_SUBIDA = Duration.ofMinutes(10);
    /** Las URL de subida aceptan solo JPEG (convenciones, «Seguridad»). */
    static final String TIPO_CONTENIDO = "image/jpeg";

    private final ObraRepositorio obras;
    private final FotoAvanceRepositorio fotos;
    private final AlmacenArchivos almacen;

    public RegistrarFotoServicio(ObraRepositorio obras, FotoAvanceRepositorio fotos, AlmacenArchivos almacen) {
        this.obras = obras;
        this.fotos = fotos;
        this.almacen = almacen;
    }

    @Override
    @Transactional(readOnly = true)
    public UrlSubida prepararSubida(UUID obraId, UUID fotoId, Usuario usuario) {
        obraDondeRegistra(obraId, usuario);
        String clave = FotoAvance.claveArchivo(obraId, fotoId);
        UrlFirmada firmada = almacen.urlSubida(clave, TIPO_CONTENIDO, VIGENCIA_SUBIDA);
        return new UrlSubida(firmada.url(), clave, firmada.expiraEn());
    }

    /**
     * Si el id ya existe en esta obra se devuelve tal cual (el celular reintentó). Si existe en otra obra, la clave
     * enviada no puede corresponder a ese registro: CLAVE_ARCHIVO_INVALIDA.
     */
    @Override
    @Transactional
    public Registro registrar(Comando comando, Usuario usuario) {
        Obra obra = obraDondeRegistra(comando.obraId(), usuario);
        Optional<FotoAvance> existente = fotos.buscarPorId(comando.id());
        if (existente.isPresent()) {
            if (!existente.get().obraId().equals(obra.id())) {
                throw new ReglaNegocioException(FotoAvance.CLAVE_ARCHIVO_INVALIDA,
                        "No se pudo asociar el archivo a este registro.");
            }
            return new Registro(existente.get(), false);
        }
        // puedeRegistrarAvance comparó el sub con un id asignado (UUID): aquí ya es un UUID válido
        FotoAvance foto = FotoAvance.registrar(comando.id(), obra.id(), comando.partidaId(), obra.idsPartidas(),
                comando.claveArchivo(), comando.tomadaEn(), UUID.fromString(usuario.id()));
        fotos.guardar(foto);
        return new Registro(foto, true);
    }

    /** La obra existe y el usuario la ve (si no, 404) y es su maestro o residente (si no, 403). */
    private Obra obraDondeRegistra(UUID obraId, Usuario usuario) {
        Obra obra = obras.buscarPorId(obraId)
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        if (!obra.puedeRegistrarAvance(usuario)) {
            throw new ReglaNegocioException(SIN_PERMISO, "Tu rol no permite esta acción.");
        }
        return obra;
    }
}
