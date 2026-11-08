package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarRespuestaContratoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica la respuesta de Finanzas a la obra. Corre en la transacción del consumidor, la misma que registra el
 * mensaje en mensaje_procesado. Si la obra ya no está en SIN_CONTRATO no cambia nada (lo decide el dominio).
 */
@Service
public class RespuestaContratoServicio implements RegistrarRespuestaContratoCasoUso {

    private final ObraRepositorio repositorio;

    public RespuestaContratoServicio(ObraRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public void contratoRegistrado(UUID obraId, UUID contratoId) {
        aplicar(obraId, obra -> obra.registrarContrato(contratoId));
    }

    @Override
    @Transactional
    public void contratoRechazado(UUID obraId, String motivo) {
        aplicar(obraId, obra -> obra.rechazarContrato(motivo));
    }

    private void aplicar(UUID obraId, Predicate<Obra> cambio) {
        Obra obra = repositorio.buscarPorId(obraId).orElseThrow(() -> new ReglaNegocioException(
                ConsultarObraCasoUso.OBRA_NO_ENCONTRADA, "No existe la obra " + obraId));
        if (cambio.test(obra)) {
            repositorio.guardar(obra);
        }
    }
}
