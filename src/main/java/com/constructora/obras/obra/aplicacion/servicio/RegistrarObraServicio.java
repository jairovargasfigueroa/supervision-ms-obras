package com.constructora.obras.obra.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.aplicacion.puerto.entrada.RegistrarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.aplicacion.puerto.salida.PublicadorEventos;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.InsumoPrevisto;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.ObraRegistrada;
import com.constructora.obras.obra.dominio.Partida;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda la obra en SIN_CONTRATO con sus partidas e insumos y deja ObraRegistrada en el outbox, en la misma
 * transacción (docs/specs/002-registrar-obra/diseno.md).
 */
@Service
public class RegistrarObraServicio implements RegistrarObraCasoUso {

    private final ObraRepositorio repositorio;
    private final PublicadorEventos publicador;
    private final Reloj reloj;

    public RegistrarObraServicio(ObraRepositorio repositorio, PublicadorEventos publicador, Reloj reloj) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ObraDetalle ejecutar(Comando comando) {
        ContratoComando datosContrato = comando.contrato();
        ContratoCopia contrato = ContratoCopia.nuevo(Dinero.de(datosContrato.monto()), datosContrato.plazoDias(),
                datosContrato.anticipoPct(), datosContrato.retencionPct());
        Obra obra = Obra.registrar(comando.nombre(), comando.ubicacion(), null, comando.fechaInicio(),
                comando.asignaciones(), contrato, comando.partidas().stream().map(RegistrarObraServicio::partida)
                        .toList());
        repositorio.guardar(obra);
        publicador.publicar(ObraRegistrada.de(obra, datosContrato.firmadoEn()));
        // Obra recién registrada: todavía no hay planillas aprobadas, ninguna partida tiene cantidad ejecutada
        return new ObraDetalle(obra, obra.indicadores(Map.of(), reloj.hoy()), null);
    }

    private static Partida partida(PartidaComando datos) {
        List<InsumoPrevisto> insumos = datos.insumos() == null ? List.of() : datos.insumos().stream()
                .map(insumo -> new InsumoPrevisto(insumo.materialId(), insumo.cantidadPorUnidad()))
                .toList();
        return Partida.nueva(datos.codigo(), datos.nombre(), datos.unidad(), datos.cantidadContratada(),
                datos.precioUnitario(), datos.meses(), insumos);
    }
}
