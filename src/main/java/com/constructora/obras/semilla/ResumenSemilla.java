package com.constructora.obras.semilla;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resultado de la carga: la fecha D, el modo y, por tabla, cuántas filas se insertaron y cuántas hay al terminar.
 * {@link #lineaInfra()} es la línea fija que lee infra para su resumen.
 */
public record ResumenSemilla(LocalDate d, ModoSemilla modo, List<String> tablasVaciadas, Map<String, Conteo> conteos,
        long outboxEvento) {

    public ResumenSemilla {
        tablasVaciadas = List.copyOf(tablasVaciadas);
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
        List<String> lineas = new ArrayList<>();
        lineas.add("Semilla de Obras terminada · D = " + d + " · modo " + modo);
        if (modo == ModoSemilla.RECARGAR) {
            lineas.add("Vaciadas " + tablasVaciadas.size() + " tablas (se conserva flyway_schema_history): "
                    + String.join(", ", tablasVaciadas));
        }
        lineas.add(detalle);
        lineas.add("outbox_evento " + outboxEvento + " (la semilla no publica eventos)");
        lineas.add(lineaInfra());
        return List.copyOf(lineas);
    }

    /**
     * Línea que copia infra a su resumen (mismo formato que Materiales), con el total de cada tabla al terminar:
     * {@code RESUMEN_SEMILLA {"servicio":"obras","fecha":"2026-10-28","totales":{"obra":3,"partida":21,…,"outbox_evento":0}}}.
     */
    public String lineaInfra() {
        String totales = CargaSemilla.TABLAS.stream().map(tabla -> "\"" + tabla + "\":" + total(tabla))
                .collect(Collectors.joining(","));
        return "RESUMEN_SEMILLA {\"servicio\":\"obras\",\"fecha\":\"" + d + "\",\"totales\":{" + totales
                + ",\"outbox_evento\":" + outboxEvento + "}}";
    }

    public record Conteo(int insertadas, long total) {
    }
}
