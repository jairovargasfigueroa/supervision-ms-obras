package com.constructora.obras.mcp;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.mcp.dto.AvanceObraSalida;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.MaterialPrevisto;
import com.constructora.obras.obra.dominio.Partida;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Herramientas MCP de solo lectura de Obras (501): adaptador de entrada, como los controladores REST. Cada método
 * llama solo a casos de uso de consulta; el ejecutor ya validó los argumentos contra el esquema de entrada
 * (EsquemasEntradaMcp) y corre la llamada en una transacción de solo lectura de 5 s como máximo. Las descripciones
 * las lee el modelo de lenguaje: dicen qué devuelve la herramienta y cuándo conviene usarla, sin instrucciones.
 */
@Component
public class HerramientasMcpObras {

    /** Respuestas compactas (501): filas máximas por herramienta. */
    static final int MAX_OBRAS = 50;
    static final int MAX_PARTIDAS = 200;

    private final ConsultarObraCasoUso consultarObra;
    private final ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto;
    private final UsuarioActual usuarioActual;
    private final Reloj reloj;

    public HerramientasMcpObras(ConsultarObraCasoUso consultarObra,
            ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto, UsuarioActual usuarioActual, Reloj reloj) {
        this.consultarObra = consultarObra;
        this.consultarMaterialPrevisto = consultarMaterialPrevisto;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    @McpTool(name = "avance_obra", generateOutputSchema = true,
            description = "Devuelve el avance físico y el plazo transcurrido de las obras, al día. Sin obraId devuelve"
                    + " las obras en ejecución ordenadas de la más atrasada a la menos. Con obraId devuelve además sus"
                    + " partidas y, si se indica periodo, el material previsto de ese periodo.",
            annotations = @McpAnnotations(title = "Avance de obras", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public AvanceObraSalida avanceObra(
            @McpToolParam(required = false, description = "Id de la obra") String obraId,
            @McpToolParam(required = false, description = "Mes AAAA-MM; solo junto con obraId") String periodo) {
        if (periodo != null && obraId == null) {
            throw ErrorHerramienta.argumentoInvalido("periodo solo se acepta junto con obraId.");
        }
        Usuario usuario = usuarioActual.obtener();
        String generadoEn = reloj.ahora().truncatedTo(ChronoUnit.SECONDS).toString();
        if (obraId == null) {
            List<AvanceObraSalida.Obra> obras = consultarObra
                    .listar(usuario, new FiltroObras(EstadoObra.EN_EJECUCION, null)).stream()
                    .map(detalle -> obra(detalle, null))
                    .sorted(Comparator.comparing(AvanceObraSalida.Obra::diferencia).reversed()
                            .thenComparing(AvanceObraSalida.Obra::nombre))
                    .toList();
            return new AvanceObraSalida(generadoEn, recortar(obras, MAX_OBRAS), null, obras.size() > MAX_OBRAS);
        }
        UUID id = UUID.fromString(obraId);
        ObraDetalle detalle = consultarObra.obtener(id, usuario);
        Map<UUID, List<AvanceObraSalida.MaterialPrevisto>> previsto = null;
        Boolean hayPlanilla = null;
        if (periodo != null) {
            ConsultarMaterialPrevistoCasoUso.Resultado resultado = consultarMaterialPrevisto.consultar(id,
                    Periodo.de(periodo), usuario);
            hayPlanilla = resultado.hayPlanilla();
            previsto = porPartida(resultado.items());
        }
        List<Partida> partidas = detalle.obra().partidas().stream()
                .sorted(Comparator.comparing(Partida::codigo)).toList();
        Map<UUID, List<AvanceObraSalida.MaterialPrevisto>> previstoFinal = previsto;
        List<AvanceObraSalida.Partida> filas = recortar(partidas, MAX_PARTIDAS).stream()
                .map(partida -> new AvanceObraSalida.Partida(partida.id().toString(), partida.codigo(),
                        partida.nombre(), partida.unidad(), partida.cantidadContratada(),
                        detalle.indicadores().ejecutadaPorPartida().get(partida.id()),
                        detalle.indicadores().avancePorPartida().get(partida.id()),
                        previstoFinal == null ? null : previstoFinal.getOrDefault(partida.id(), List.of())))
                .toList();
        return new AvanceObraSalida(generadoEn, List.of(obra(detalle, filas)), hayPlanilla,
                partidas.size() > MAX_PARTIDAS);
    }

    private static AvanceObraSalida.Obra obra(ObraDetalle detalle, List<AvanceObraSalida.Partida> partidas) {
        BigDecimal avance = detalle.indicadores().avancePct();
        BigDecimal plazo = detalle.indicadores().plazoPct();
        return new AvanceObraSalida.Obra(detalle.obra().id().toString(), detalle.obra().nombre(),
                detalle.obra().estado().name(), detalle.obra().fechaInicio().toString(),
                detalle.obra().contrato().plazoDias(), avance, plazo, plazo.subtract(avance),
                detalle.indicadores().enRiesgo(),
                detalle.ultimaPlanilla() == null ? null
                        : new AvanceObraSalida.UltimaPlanilla(detalle.ultimaPlanilla().periodo().toString(),
                                detalle.ultimaPlanilla().estado().name()),
                partidas);
    }

    /** Material previsto agrupado por partida, en el orden del caso de uso (código de partida y material). */
    private static Map<UUID, List<AvanceObraSalida.MaterialPrevisto>> porPartida(List<MaterialPrevisto> items) {
        return items.stream().collect(Collectors.groupingBy(MaterialPrevisto::partidaId, LinkedHashMap::new,
                Collectors.mapping(item -> new AvanceObraSalida.MaterialPrevisto(item.materialId(),
                        item.cantidadPrevista()), Collectors.toList())));
    }

    static <T> List<T> recortar(List<T> filas, int maximo) {
        return filas.size() > maximo ? List.copyOf(filas.subList(0, maximo)) : filas;
    }
}
