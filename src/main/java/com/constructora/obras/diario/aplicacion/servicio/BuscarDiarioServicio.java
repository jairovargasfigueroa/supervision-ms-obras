package com.constructora.obras.diario.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.aplicacion.puerto.entrada.BuscarDiarioCasoUso;
import com.constructora.obras.diario.aplicacion.puerto.entrada.ConsultarDiarioCasoUso;
import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 501 · Búsqueda en el diario para buscar_diario, con la visibilidad de la 103 (agente_ia ve todas, como gerente). */
@Service
public class BuscarDiarioServicio implements BuscarDiarioCasoUso {

    private final ObraRepositorio obras;
    private final EntradaDiarioRepositorio entradas;

    public BuscarDiarioServicio(ObraRepositorio obras, EntradaDiarioRepositorio entradas) {
        this.obras = obras;
        this.entradas = entradas;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado buscar(Busqueda busqueda, Usuario usuario) {
        Obra obra = obras.buscarPorId(busqueda.obraId())
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        if (!obra.puedeVerDiario(usuario)) {
            throw new ReglaNegocioException(ConsultarDiarioCasoUso.SIN_PERMISO, "Tu rol no permite esta acción.");
        }
        return new Resultado(
                entradas.buscar(obra.id(), busqueda.desde(), busqueda.hasta(), busqueda.texto(), busqueda.limite()),
                entradas.contar(obra.id(), busqueda.desde(), busqueda.hasta(), busqueda.texto()));
    }
}
