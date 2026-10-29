package com.constructora.obras.planilla.aplicacion.servicio;

import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ElaborarPlanillaCasoUso;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.PlanillaDetalle;
import com.constructora.obras.planilla.aplicacion.puerto.salida.PlanillaRepositorio;
import com.constructora.obras.planilla.aplicacion.servicio.PlanillasDeObra.PlanillaYObra;
import com.constructora.obras.planilla.dominio.Planilla;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crear, guardar y presentar planillas (007). Las reglas (periodo, límite contra lo contratado, fotos del periodo,
 * estados, subtotal) están en el dominio Planilla; aquí se cargan los datos que necesita y se guarda. La 010
 * publicará PlanillaPresentada al presentar (pospuesta).
 */
@Service
public class ElaborarPlanillaServicio implements ElaborarPlanillaCasoUso {

    private final PlanillaRepositorio planillas;
    private final FotoAvanceRepositorio fotos;
    private final Reloj reloj;
    private final PlanillasDeObra comunes;

    public ElaborarPlanillaServicio(ObraRepositorio obras, PlanillaRepositorio planillas,
            FotoAvanceRepositorio fotos, Reloj reloj) {
        this.planillas = planillas;
        this.fotos = fotos;
        this.reloj = reloj;
        this.comunes = new PlanillasDeObra(obras, planillas);
    }

    @Override
    @Transactional
    public PlanillaDetalle crear(UUID obraId, Periodo periodo, Usuario usuario) {
        Obra obra = comunes.obraVisible(obraId, usuario);
        PlanillasDeObra.exigirResidente(obra, usuario);
        Planilla planilla = Planilla.crear(obra, periodo, planillas.siguienteNumero(obra.id()),
                planillas.hayAbierta(obra.id()), planillas.periodoUltima(obra.id()), reloj.hoy());
        return comunes.detalle(planillas.guardar(planilla), obra);
    }

    /** Orden: editable → versión → cantidades (límite) → fotos del periodo. */
    @Override
    @Transactional
    public PlanillaDetalle guardar(Guardado guardado, Usuario usuario) {
        PlanillaYObra encontrada = comunes.planillaVisible(guardado.planillaId(), usuario);
        Obra obra = encontrada.obra();
        Planilla planilla = encontrada.planilla();
        PlanillasDeObra.exigirResidente(obra, usuario);
        planilla.verificarEdicion(guardado.version());
        planilla.actualizarLineas(guardado.lineas(), planillas.acumuladosAnteriores(obra.id(), planilla.periodo()),
                obra);
        Map<UUID, FotoAvance> encontradas = fotos.buscarPorIds(guardado.fotoIds()).stream()
                .collect(Collectors.toMap(FotoAvance::id, Function.identity()));
        planilla.adjuntarFotos(guardado.fotoIds(), encontradas);
        return comunes.detalle(planillas.guardar(planilla), obra);
    }

    @Override
    @Transactional
    public PlanillaDetalle presentar(UUID planillaId, int version, Usuario usuario) {
        PlanillaYObra encontrada = comunes.planillaVisible(planillaId, usuario);
        Obra obra = encontrada.obra();
        Planilla planilla = encontrada.planilla();
        PlanillasDeObra.exigirResidente(obra, usuario);
        planilla.presentar(version);
        Planilla guardada = planillas.guardar(planilla);
        // Las fotos de respaldo quedan visibles para el cliente (docs/reglas-negocio.md, «Fotos de avance»)
        fotos.publicar(guardada.fotoIds());
        return comunes.detalle(guardada, obra);
    }
}
