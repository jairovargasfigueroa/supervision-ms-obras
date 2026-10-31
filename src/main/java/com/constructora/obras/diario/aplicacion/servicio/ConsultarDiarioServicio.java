package com.constructora.obras.diario.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.aplicacion.puerto.entrada.ConsultarDiarioCasoUso;
import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.diario.dominio.RangoDiario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista del diario de una obra por rango de fechas (103, docs/specs/103-diario-obra/diseno.md). */
@Service
public class ConsultarDiarioServicio implements ConsultarDiarioCasoUso {

    private final ObraRepositorio obras;
    private final EntradaDiarioRepositorio entradas;
    private final Reloj reloj;

    public ConsultarDiarioServicio(ObraRepositorio obras, EntradaDiarioRepositorio entradas, Reloj reloj) {
        this.obras = obras;
        this.entradas = entradas;
        this.reloj = reloj;
    }

    /** Orden: rango (DATOS_INVALIDOS, RANGO_INVALIDO) → obra visible y no solo como cliente (404) → papel (403). */
    @Override
    @Transactional(readOnly = true)
    public List<EntradaDiario> listar(Consulta consulta, Usuario usuario) {
        RangoDiario rango = RangoDiario.de(consulta.desde(), consulta.hasta(), reloj.hoy());
        Obra obra = obras.buscarPorId(consulta.obraId())
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .filter(encontrada -> encontrada.puedeVerDiario(usuario) || !encontrada.veSoloComoCliente(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        if (!obra.puedeVerDiario(usuario)) {
            throw new ReglaNegocioException(SIN_PERMISO, "Tu rol no permite esta acción.");
        }
        return entradas.listar(obra.id(), rango.desde(), rango.hasta());
    }
}
