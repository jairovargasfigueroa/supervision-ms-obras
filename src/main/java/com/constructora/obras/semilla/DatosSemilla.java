package com.constructora.obras.semilla;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contenido de {@code semilla/obras.json}: la parte de Obras de docs/datos-prueba.md con las fechas relativas a D
 * sin resolver. Solo trae lo que se escribe a mano (ids, nombres, cantidades, precios); los montos de las líneas, los
 * totales de las planillas y el número del cobro los calcula {@link PlanSemilla}.
 */
public record DatosSemilla(
        @JsonProperty("_fuente") String fuente,
        /** Código del material → id en Materiales (docs/datos-prueba.md, sección 5). */
        Map<String, String> materiales,
        /** Receta → material previsto por unidad de la partida, por código de material (sección 6.1). */
        Map<String, Map<String, BigDecimal>> recetas,
        List<ObraSemilla> obras,
        List<FotoSemilla> fotos,
        UUID fotosRegistradasPor,
        /** 301: versión del modelo con la que figuran analizadas las fotos (sin personas: 0, 0 y 0; sección 13). */
        String fotosVersionModeloEpp,
        List<PlanillaSemilla> planillas) {

    public static final String RECURSO = "semilla/obras.json";

    /**
     * 603: historia de demostración (obras 4 a 6, generada por {@code infra/semilla/historia/generar.mjs}). Trae solo
     * obras, fotos y planillas; el catálogo, las recetas y quién registró las fotos son los de la 011.
     */
    public static final String RECURSO_HISTORIA = "semilla/obras-historia.json";

    /** Lee el archivo de datos del classpath. Un campo desconocido es un error (protege contra errores al copiar). */
    public static DatosSemilla leer() {
        return leerRecurso(RECURSO);
    }

    /**
     * Los datos de la 011 y, con {@code conHistoria} (603, {@code SEMILLA_HISTORIA=si}), las obras, fotos y planillas de
     * la historia agregadas a las suyas: todo se valida y se carga junto.
     */
    public static DatosSemilla leer(boolean conHistoria) {
        DatosSemilla base = leer();
        if (!conHistoria) {
            return base;
        }
        DatosSemilla historia = leerRecurso(RECURSO_HISTORIA);
        return new DatosSemilla(base.fuente(), base.materiales(), base.recetas(), unir(base.obras(), historia.obras()),
                unir(base.fotos(), historia.fotos()), base.fotosRegistradasPor(), base.fotosVersionModeloEpp(),
                unir(base.planillas(), historia.planillas()));
    }

    private static <T> List<T> unir(List<T> base, List<T> historia) {
        java.util.ArrayList<T> unidas = new java.util.ArrayList<>(base);
        if (historia != null) {
            unidas.addAll(historia);
        }
        return List.copyOf(unidas);
    }

    private static DatosSemilla leerRecurso(String recurso) {
        JsonMapper json = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        try (InputStream entrada = DatosSemilla.class.getClassLoader().getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró " + recurso + " en el classpath");
            }
            return json.readValue(entrada, DatosSemilla.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
    }

    /**
     * Obra con la copia del contrato, sus asignaciones y su presupuesto. {@code fechaInicio} es relativa (D-n);
     * {@code zona} (401) es NORTE, SUR, ESTE, OESTE, CENTRO o null.
     */
    public record ObraSemilla(UUID id, String nombre, String ubicacion, String zona, String estado, UUID clienteUsuarioId,
            UUID residenteUsuarioId, UUID maestroUsuarioId, UUID almaceneroUsuarioId, UUID contratoId,
            ContratoSemilla contrato, String fechaInicio, List<PartidaSemilla> partidas) {
    }

    public record ContratoSemilla(BigDecimal monto, int plazoDias, BigDecimal anticipoPct, BigDecimal retencionPct) {
    }

    /** {@code meses}: M-k o M-k..M+j; {@code receta}: clave de {@code recetas} o null si no lleva insumos. */
    public record PartidaSemilla(UUID id, String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
            BigDecimal precioUnitario, String meses, String receta) {
    }

    /** Foto de avance; {@code partida} es el código de la partida de su obra y {@code tomadaEn}, M-k/dd hh:mm o D-n hh:mm. */
    public record FotoSemilla(UUID id, UUID obraId, String partida, String tomadaEn, boolean publicadaCliente) {
    }

    /**
     * Planilla con la cantidad del periodo por código de partida (las que no figuran van en 0). {@code periodo} es
     * M-k; {@code cobro}, solo en las APROBADA; {@code fotos}, ids de las fotos adjuntas.
     */
    /** presentadaEn (501): última vez que pasó a PRESENTADA (D-n hh:mm); null si no se carga. */
    public record PlanillaSemilla(UUID id, UUID obraId, int numero, String periodo, String estado,
            Map<String, BigDecimal> cantidades, String observacion, CobroSemilla cobro, List<UUID> fotos,
            String presentadaEn) {
    }

    /** Cobro que emitió Finanzas al aprobarse la planilla: su id y cuándo se emitió (D-n hh:mm). */
    public record CobroSemilla(UUID id, String emitidoEn) {
    }
}
