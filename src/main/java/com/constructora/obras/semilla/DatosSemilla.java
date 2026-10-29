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
        List<PlanillaSemilla> planillas) {

    public static final String RECURSO = "semilla/obras.json";

    /** Lee el archivo de datos del classpath. Un campo desconocido es un error (protege contra errores al copiar). */
    public static DatosSemilla leer() {
        JsonMapper json = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        try (InputStream entrada = DatosSemilla.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró " + RECURSO + " en el classpath");
            }
            return json.readValue(entrada, DatosSemilla.class);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RECURSO, e);
        }
    }

    /** Obra con la copia del contrato, sus asignaciones y su presupuesto. {@code fechaInicio} es relativa (D-n). */
    public record ObraSemilla(UUID id, String nombre, String ubicacion, String estado, UUID clienteUsuarioId,
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
    public record PlanillaSemilla(UUID id, UUID obraId, int numero, String periodo, String estado,
            Map<String, BigDecimal> cantidades, String observacion, CobroSemilla cobro, List<UUID> fotos) {
    }

    /** Cobro que emitió Finanzas al aprobarse la planilla: su id y cuándo se emitió (D-n hh:mm). */
    public record CobroSemilla(UUID id, String emitidoEn) {
    }
}
