package com.constructora.obras.avance.aplicacion.servicio;

import com.constructora.obras.avance.aplicacion.puerto.entrada.ConsultarFotosCasoUso;
import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.AlmacenArchivos;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista de fotos de avance con URL de descarga firmada (005). */
@Service
public class ConsultarFotosServicio implements ConsultarFotosCasoUso {

    /** Vigencia de cada URL de descarga (diseño de la 005). */
    static final Duration VIGENCIA_DESCARGA = Duration.ofMinutes(30);

    private final ObraRepositorio obras;
    private final FotoAvanceRepositorio fotos;
    private final AlmacenArchivos almacen;

    public ConsultarFotosServicio(ObraRepositorio obras, FotoAvanceRepositorio fotos, AlmacenArchivos almacen) {
        this.obras = obras;
        this.fotos = fotos;
        this.almacen = almacen;
    }

    /**
     * Si el usuario no ve la obra: OBRA_NO_ENCONTRADA. Si la ve solo como cliente, recibe solo las publicadas
     * (requisitos de la 005, criterio 8). Una página vacía no necesita el almacenamiento; si hay fotos y S3 no está
     * configurado, AlmacenNoDisponibleException (503).
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<FotoListada> listar(Consulta consulta, Usuario usuario) {
        Obra obra = obras.buscarPorId(consulta.obraId())
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        boolean soloPublicadas = !obra.esVisiblePara(sinRolCliente(usuario));
        Pagina<FotoAvance> pagina = fotos.listar(obra.id(), consulta.partidaId(), soloPublicadas,
                consulta.paginacion());
        return pagina.map(foto -> new FotoListada(foto,
                almacen.urlDescarga(foto.claveArchivo(), VIGENCIA_DESCARGA).url()));
    }

    private static Usuario sinRolCliente(Usuario usuario) {
        Set<String> roles = new HashSet<>(usuario.roles());
        roles.remove("cliente");
        return new Usuario(usuario.id(), roles);
    }
}
