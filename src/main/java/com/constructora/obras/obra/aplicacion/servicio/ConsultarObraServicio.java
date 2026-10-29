package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle y lista de obras según quién pregunta. Una obra que el usuario no ve responde igual que una que no
 * existe (OBRA_NO_ENCONTRADA), para no revelar que existe.
 */
@Service
public class ConsultarObraServicio implements ConsultarObraCasoUso {

    private final ObraRepositorio repositorio;
    private final Reloj reloj;

    public ConsultarObraServicio(ObraRepositorio repositorio, Reloj reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public ObraDetalle obtener(UUID obraId, Usuario usuario) {
        Obra obra = repositorio.buscarPorId(obraId)
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(OBRA_NO_ENCONTRADA, "Obra no encontrada"));
        return detalle(obra, reloj.hoy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObraDetalle> listar(Usuario usuario) {
        LocalDate hoy = reloj.hoy();
        return visibles(usuario).stream().map(obra -> detalle(obra, hoy)).toList();
    }

    private List<Obra> visibles(Usuario usuario) {
        if (usuario.tiene("admin") || usuario.tiene("gerente")) {
            return repositorio.listarTodas();
        }
        boolean residente = usuario.tiene("residente");
        boolean maestro = usuario.tiene("maestro");
        boolean almacenero = usuario.tiene("almacenero");
        boolean cliente = usuario.tiene("cliente");
        Optional<UUID> id = comoUuid(usuario.id());
        if (id.isEmpty() || !(residente || maestro || almacenero || cliente)) {
            return List.of();
        }
        return repositorio.listarAsignadas(id.get(), residente, maestro, almacenero, cliente);
    }

    /**
     * Las planillas aprobadas llegan con la 007 y la 008: hasta entonces ninguna partida tiene cantidad ejecutada,
     * así que el avance es 0; el plazo y el riesgo se calculan con la regla de docs/reglas-negocio.md.
     */
    private static ObraDetalle detalle(Obra obra, LocalDate hoy) {
        return new ObraDetalle(obra, obra.indicadores(Map.of(), hoy));
    }

    /** Los ids de Keycloak son UUID; un sub que no lo es no puede estar asignado a ninguna obra. */
    private static Optional<UUID> comoUuid(String valor) {
        try {
            return Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
