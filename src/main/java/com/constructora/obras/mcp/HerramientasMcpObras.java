package com.constructora.obras.mcp;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.compartido.infraestructura.seguridad.UsuarioActual;
import com.constructora.obras.diario.aplicacion.puerto.entrada.BuscarDiarioCasoUso;
import com.constructora.obras.mcp.dto.AvanceObraSalida;
import com.constructora.obras.mcp.dto.BuscarDiarioSalida;
import com.constructora.obras.mcp.dto.PlanillasEstadoSalida;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarMaterialPrevistoCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ObraDetalle;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.MaterialPrevisto;
import com.constructora.obras.obra.dominio.Partida;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** buscar_diario (501): rango máximo, valores por defecto y texto máximo de cada entrada. */
    static final int RANGO_MAXIMO_DIAS = 62;
    static final int DIAS_POR_DEFECTO = 14;
    static final int LIMITE_DIARIO = 14;
    static final int TEXTO_DIARIO_MAXIMO = 1000;
    /** planillas_estado (501): límite por defecto y observación máxima. */
    static final int LIMITE_PLANILLAS = 30;
    static final int OBSERVACION_MAXIMA = 500;
    static final Set<EstadoPlanilla> ESTADOS_POR_DEFECTO = EnumSet.complementOf(EnumSet.of(EstadoPlanilla.APROBADA));

    private final ConsultarObraCasoUso consultarObra;
    private final ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto;
    private final BuscarDiarioCasoUso buscarDiario;
    private final ConsultarPlanillasEstadoCasoUso consultarPlanillas;
    private final UsuarioActual usuarioActual;
    private final Reloj reloj;

    public HerramientasMcpObras(ConsultarObraCasoUso consultarObra,
            ConsultarMaterialPrevistoCasoUso consultarMaterialPrevisto, BuscarDiarioCasoUso buscarDiario,
            ConsultarPlanillasEstadoCasoUso consultarPlanillas, UsuarioActual usuarioActual, Reloj reloj) {
        this.consultarObra = consultarObra;
        this.consultarMaterialPrevisto = consultarMaterialPrevisto;
        this.buscarDiario = buscarDiario;
        this.consultarPlanillas = consultarPlanillas;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    @McpTool(name = "avance_obra", generateOutputSchema = true,
            description = "Devuelve el avance físico y el plazo transcurrido de las obras, al día. Sin obraId devuelve"
                    + " las obras en ejecución ordenadas de la más atrasada a la menos; si además se indica periodo,"
                    + " cada una trae sus partidas con el material previsto de ese periodo. Con obraId devuelve además"
                    + " sus partidas y, si se indica periodo, el material previsto de ese periodo.",
            annotations = @McpAnnotations(title = "Avance de obras", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public AvanceObraSalida avanceObra(
            @McpToolParam(required = false, description = "Id de la obra") String obraId,
            @McpToolParam(required = false, description = "Mes AAAA-MM del material previsto") String periodo) {
        Usuario usuario = usuarioActual.obtener();
        String generadoEn = reloj.ahora().truncatedTo(ChronoUnit.SECONDS).toString();
        if (obraId == null) {
            return enEjecucion(usuario, periodo == null ? null : Periodo.de(periodo), generadoEn);
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

    /**
     * avance_obra sin obraId: obras en ejecución de la más atrasada a la menos (hasta MAX_OBRAS). Sin periodo van con
     * partidas null; con periodo (505, lote) cada una con sus partidas reducidas al material previsto del periodo.
     */
    private AvanceObraSalida enEjecucion(Usuario usuario, Periodo periodo, String generadoEn) {
        List<ObraDetalle> ordenadas = consultarObra.listar(usuario, new FiltroObras(EstadoObra.EN_EJECUCION, null))
                .stream()
                .sorted(Comparator.comparing(HerramientasMcpObras::diferencia).reversed()
                        .thenComparing(detalle -> detalle.obra().nombre()))
                .toList();
        List<ObraDetalle> devueltas = recortar(ordenadas, MAX_OBRAS);
        Map<UUID, List<AvanceObraSalida.Partida>> partidas = periodo == null ? Map.of()
                : partidasDelLote(devueltas, periodo, usuario);
        List<AvanceObraSalida.Obra> obras = devueltas.stream()
                .map(detalle -> obra(detalle, periodo == null ? null
                        : partidas.getOrDefault(detalle.obra().id(), List.of())))
                .toList();
        return new AvanceObraSalida(generadoEn, obras, null, ordenadas.size() > MAX_OBRAS);
    }

    /**
     * Lote (505): partidas de cada obra, por código, reducidas a partidaId, codigo, nombre y el material previsto del
     * periodo (el cálculo de GET /obras/{id}/material-previsto). Se omiten las que quedan sin material previsto: las
     * que no tienen insumos y todas las de una obra sin planilla en el periodo. Una sola lectura de las planillas.
     */
    private Map<UUID, List<AvanceObraSalida.Partida>> partidasDelLote(List<ObraDetalle> detalles, Periodo periodo,
            Usuario usuario) {
        Map<UUID, ConsultarMaterialPrevistoCasoUso.Resultado> resultados = consultarMaterialPrevisto.consultar(
                detalles.stream().map(detalle -> detalle.obra().id()).toList(), periodo, usuario);
        Map<UUID, List<AvanceObraSalida.Partida>> porObra = new HashMap<>();
        for (ObraDetalle detalle : detalles) {
            ConsultarMaterialPrevistoCasoUso.Resultado resultado = resultados.get(detalle.obra().id());
            Map<UUID, List<AvanceObraSalida.MaterialPrevisto>> previsto = resultado == null ? Map.of()
                    : porPartida(resultado.items());
            porObra.put(detalle.obra().id(), detalle.obra().partidas().stream()
                    .filter(partida -> previsto.containsKey(partida.id()))
                    .sorted(Comparator.comparing(Partida::codigo))
                    .map(partida -> AvanceObraSalida.Partida.reducida(partida.id().toString(), partida.codigo(),
                            partida.nombre(), previsto.get(partida.id())))
                    .toList());
        }
        return porObra;
    }

    /** diferencia = plazoPct − avancePct (lo que la obra va atrasada). */
    private static BigDecimal diferencia(ObraDetalle detalle) {
        return detalle.indicadores().plazoPct().subtract(detalle.indicadores().avancePct());
    }

    @McpTool(name = "buscar_diario", generateOutputSchema = true,
            description = "Devuelve las entradas del diario de obra (clima y texto) de una obra en un rango de fechas,"
                    + " opcionalmente filtradas por una palabra. Sirve para buscar la causa de un atraso.",
            annotations = @McpAnnotations(title = "Diario de obra", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public BuscarDiarioSalida buscarDiario(
            @McpToolParam(description = "Id de la obra") String obraId,
            @McpToolParam(required = false, description = "Primera fecha AAAA-MM-DD") String desde,
            @McpToolParam(required = false, description = "Última fecha AAAA-MM-DD") String hasta,
            @McpToolParam(required = false, description = "Palabra a buscar") String texto,
            @McpToolParam(required = false, description = "Entradas máximas") Integer limite) {
        LocalDate hoy = reloj.hoy();
        LocalDate fin = hasta == null ? hoy : fecha(hasta, "hasta");
        LocalDate inicio = desde == null ? hoy.minusDays(DIAS_POR_DEFECTO) : fecha(desde, "desde");
        if (inicio.isAfter(fin)) {
            throw ErrorHerramienta.argumentoInvalido("desde no puede ser posterior a hasta.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > RANGO_MAXIMO_DIAS) {
            throw ErrorHerramienta.argumentoInvalido("El rango de fechas no puede pasar de " + RANGO_MAXIMO_DIAS
                    + " días.");
        }
        int maximo = limite == null ? LIMITE_DIARIO : limite;
        BuscarDiarioCasoUso.Resultado resultado = buscarDiario.buscar(
                new BuscarDiarioCasoUso.Busqueda(UUID.fromString(obraId), inicio, fin, texto, maximo),
                usuarioActual.obtener());
        List<BuscarDiarioSalida.Entrada> entradas = resultado.entradas().stream()
                .map(entrada -> new BuscarDiarioSalida.Entrada(entrada.fecha().toString(), entrada.clima().name(),
                        recortarTexto(entrada.texto(), TEXTO_DIARIO_MAXIMO),
                        largo(entrada.texto()) > TEXTO_DIARIO_MAXIMO))
                .toList();
        return new BuscarDiarioSalida(obraId, entradas, resultado.total(), resultado.total() > maximo);
    }

    @McpTool(name = "planillas_estado", generateOutputSchema = true,
            description = "Devuelve las planillas de avance, al día, con su estado, neto, observación y días desde que"
                    + " se presentaron. Sirve para detectar planillas pendientes o sin respuesta del cliente.",
            annotations = @McpAnnotations(title = "Estado de las planillas", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public PlanillasEstadoSalida planillasEstado(
            @McpToolParam(required = false, description = "Id de la obra") String obraId,
            @McpToolParam(required = false, description = "Estados que se devuelven") List<String> estados,
            @McpToolParam(required = false, description = "Mes AAAA-MM desde el que se devuelven") String desdePeriodo,
            @McpToolParam(required = false, description = "Planillas máximas") Integer limite) {
        Set<EstadoPlanilla> pedidos = estados == null ? ESTADOS_POR_DEFECTO
                : estados.stream().map(EstadoPlanilla::valueOf).collect(Collectors.toSet());
        ConsultarPlanillasEstadoCasoUso.Resultado resultado = consultarPlanillas.consultar(
                new ConsultarPlanillasEstadoCasoUso.Filtro(obraId == null ? null : UUID.fromString(obraId), pedidos,
                        desdePeriodo == null ? null : Periodo.de(desdePeriodo),
                        limite == null ? LIMITE_PLANILLAS : limite),
                usuarioActual.obtener());
        LocalDate hoy = reloj.hoy();
        List<PlanillasEstadoSalida.Planilla> planillas = resultado.planillas().stream()
                .map(planilla -> new PlanillasEstadoSalida.Planilla(planilla.planillaId().toString(),
                        planilla.obraId().toString(), planilla.obraNombre(), planilla.numero(),
                        planilla.periodo().toString(), planilla.estado().name(), planilla.neto(),
                        planilla.observacion() == null ? null
                                : recortarTexto(planilla.observacion(), OBSERVACION_MAXIMA),
                        planilla.presentadaEn() == null ? null
                                : planilla.presentadaEn().truncatedTo(ChronoUnit.SECONDS).toString(),
                        diasDesdePresentacion(planilla, hoy),
                        planilla.cobroId() == null ? null : planilla.cobroId().toString()))
                .toList();
        return new PlanillasEstadoSalida(planillas, resultado.hayMas());
    }

    /** Días calendario en La Paz desde la presentación hasta hoy, solo para las PRESENTADA. */
    private static Long diasDesdePresentacion(ConsultarPlanillasEstadoCasoUso.PlanillaEstado planilla,
            LocalDate hoy) {
        if (planilla.estado() != EstadoPlanilla.PRESENTADA || planilla.presentadaEn() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(planilla.presentadaEn().atZone(Reloj.ZONA_NEGOCIO).toLocalDate(), hoy);
    }

    private static LocalDate fecha(String texto, String campo) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw ErrorHerramienta.argumentoInvalido(campo + " no es una fecha válida (AAAA-MM-DD).");
        }
    }

    /** Largo en caracteres (puntos de código), como cuenta el esquema. */
    private static int largo(String texto) {
        return texto.codePointCount(0, texto.length());
    }

    /** Recorta a un máximo de caracteres (puntos de código) sin partir un carácter. */
    static String recortarTexto(String texto, int maximo) {
        if (largo(texto) <= maximo) {
            return texto;
        }
        return texto.substring(0, texto.offsetByCodePoints(0, maximo));
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
