package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PlanillasObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.dominio.PlanillaCorta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle y lista de obras según quién pregunta, con los indicadores calculados al leer (006): avance con las
 * planillas APROBADA, plazo transcurrido a la fecha del Reloj y riesgo. Una obra que el usuario no ve responde igual
 * que una que no existe (OBRA_NO_ENCONTRADA), para no revelar que existe.
 */
@Service
public class ConsultarObraServicio implements ConsultarObraCasoUso {

    private final ObraRepositorio repositorio;
    private final PlanillasObraRepositorio planillas;
    private final Reloj reloj;

    public ConsultarObraServicio(ObraRepositorio repositorio, PlanillasObraRepositorio planillas, Reloj reloj) {
        this.repositorio = repositorio;
        this.planillas = planillas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public ObraDetalle obtener(UUID obraId, Usuario usuario) {
        Obra obra = repositorio.buscarPorId(obraId)
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(OBRA_NO_ENCONTRADA, "Obra no encontrada"));
        return detalles(List.of(obra), usuario).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObraDetalle> listar(Usuario usuario, FiltroObras filtro) {
        return detalles(visibles(usuario, filtro), usuario);
    }

    private List<Obra> visibles(Usuario usuario, FiltroObras filtro) {
        if (usuario.tiene("admin") || usuario.tiene("gerente")) {
            return repositorio.listarTodas(filtro);
        }
        boolean residente = usuario.tiene("residente");
        boolean maestro = usuario.tiene("maestro");
        boolean almacenero = usuario.tiene("almacenero");
        boolean cliente = usuario.tiene("cliente");
        Optional<UUID> id = comoUuid(usuario.id());
        if (id.isEmpty() || !(residente || maestro || almacenero || cliente)) {
            return List.of();
        }
        return repositorio.listarAsignadas(id.get(), residente, maestro, almacenero, cliente, filtro);
    }

    /**
     * Indicadores y última planilla de cada obra, con pocas consultas a las planillas para toda la lista. En las obras
     * que el usuario ve solo como cliente (por ejemplo desde el portal), la última planilla no cuenta los BORRADOR,
     * igual que la lista de planillas (007).
     */
    private List<ObraDetalle> detalles(List<Obra> obras, Usuario usuario) {
        if (obras.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = obras.stream().map(Obra::id).toList();
        List<UUID> soloCliente = obras.stream().filter(obra -> obra.veSoloComoCliente(usuario)).map(Obra::id).toList();
        List<UUID> internas = ids.stream().filter(id -> !soloCliente.contains(id)).toList();
        Map<UUID, Map<UUID, BigDecimal>> ejecutadas = planillas.ejecutadaAcumulada(ids);
        Map<UUID, PlanillaCorta> ultimas = planillas.ultimas(internas);
        Map<UUID, PlanillaCorta> ultimasCliente = planillas.ultimasSinBorrador(soloCliente);
        LocalDate hoy = reloj.hoy();
        return obras.stream()
                .map(obra -> new ObraDetalle(obra,
                        obra.indicadores(ejecutadas.getOrDefault(obra.id(), Map.of()), hoy),
                        soloCliente.contains(obra.id()) ? ultimasCliente.get(obra.id()) : ultimas.get(obra.id())))
                .toList();
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
