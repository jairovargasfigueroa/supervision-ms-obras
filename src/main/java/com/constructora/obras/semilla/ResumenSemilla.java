package com.constructora.obras.semilla;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resultado de la carga: la fecha D, el modo y, por tabla, cuántas filas se insertaron y cuántas hay al terminar.
 * {@link #lineaInfra()} es la línea fija que lee infra para su resumen.
 */
public record ResumenSemilla(LocalDate d, ModoSemilla modo, Map<String, Conteo> conteos, long outboxEvento) {

    /** Nombre de cada tabla en la línea para infra (en el orden de CargaSemilla.TABLAS). */
    private static final Map<String, String> CLAVES = Map.of("obra", "obras", "partida", "partidas",
            "insumo_previsto", "insumos", "foto_avance", "fotos", "planilla", "planillas", "linea_planilla", "lineas",
            "planilla_foto", "fotosAdjuntas");

    public ResumenSemilla {
        conteos = Map.copyOf(conteos);
    }

    public long total(String tabla) {
        return conteos.get(tabla).total();
    }

    public int insertadas(String tabla) {
        return conteos.get(tabla).insertadas();
    }

    /** Líneas del registro para quien la corre a mano. */
    public List<String> lineas() {
        String detalle = CargaSemilla.TABLAS.stream()
                .map(tabla -> tabla + " " + total(tabla) + " (" + insertadas(tabla) + " nuevas)")
                .collect(Collectors.joining(" · "));
        return List.of("Semilla de Obras terminada · D = " + d + " · modo " + modo, detalle,
                "outbox_evento " + outboxEvento + " (la semilla no publica eventos)", lineaInfra());
    }

    /** {@code SEMILLA_RESUMEN servicio=obras fecha=2026-10-28 modo=cargar obras=3 partidas=21 …}. */
    public String lineaInfra() {
        return "SEMILLA_RESUMEN servicio=obras fecha=" + d + " modo=" + modo + " "
                + CargaSemilla.TABLAS.stream().map(tabla -> CLAVES.get(tabla) + "=" + total(tabla))
                        .collect(Collectors.joining(" "));
    }

    public record Conteo(int insertadas, long total) {
    }
}
