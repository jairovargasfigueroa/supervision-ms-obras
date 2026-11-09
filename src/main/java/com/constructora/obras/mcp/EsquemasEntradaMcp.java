package com.constructora.obras.mcp;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Esquemas de entrada de las herramientas (501, «Herramientas»): JSON Schema estricto (additionalProperties: false,
 * formatos, rangos y valores permitidos). Reemplazan al que Spring AI genera desde los parámetros, que no lleva
 * restricciones; el ejecutor valida cada llamada contra estos esquemas antes de tocar la base.
 */
final class EsquemasEntradaMcp {

    static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    static final String PERIODO = "^\\\\d{4}-(0[1-9]|1[0-2])$";
    static final String FECHA = "^\\\\d{4}-\\\\d{2}-\\\\d{2}$";

    private static final Map<String, String> ESQUEMAS = Map.of(
            "avance_obra", """
                    {
                      "type": "object",
                      "properties": {
                        "obraId": {
                          "type": "string", "format": "uuid", "pattern": "%1$s",
                          "description": "Id de la obra. Sin obraId devuelve las obras en ejecución."
                        },
                        "periodo": {
                          "type": "string", "pattern": "%2$s",
                          "description": "Mes AAAA-MM del material previsto. Sin obraId, el de cada obra en ejecución."
                        }
                      },
                      "additionalProperties": false
                    }""".formatted(UUID, PERIODO),
            "buscar_diario", """
                    {
                      "type": "object",
                      "properties": {
                        "obraId": {
                          "type": "string", "format": "uuid", "pattern": "%1$s",
                          "description": "Id de la obra."
                        },
                        "desde": {
                          "type": "string", "format": "date", "pattern": "%2$s",
                          "description": "Primera fecha AAAA-MM-DD. Por defecto, hoy menos 14 días."
                        },
                        "hasta": {
                          "type": "string", "format": "date", "pattern": "%2$s",
                          "description": "Última fecha AAAA-MM-DD. Por defecto, hoy. Rango máximo: 62 días."
                        },
                        "texto": {
                          "type": "string", "minLength": 2, "maxLength": 50,
                          "description": "Palabra que debe contener el texto de la entrada, sin distinguir mayúsculas."
                        },
                        "limite": {
                          "type": "integer", "minimum": 1, "maximum": 30, "default": 14,
                          "description": "Entradas máximas."
                        }
                      },
                      "required": ["obraId"],
                      "additionalProperties": false
                    }""".formatted(UUID, FECHA),
            "planillas_estado", """
                    {
                      "type": "object",
                      "properties": {
                        "obraId": {
                          "type": "string", "format": "uuid", "pattern": "%1$s",
                          "description": "Id de la obra. Sin obraId, las planillas de todas las obras."
                        },
                        "estados": {
                          "type": "array", "minItems": 1, "maxItems": 5, "uniqueItems": true,
                          "items": { "type": "string", "enum": ["BORRADOR", "PRESENTADA", "APROBANDO", "APROBADA", "OBSERVADA"] },
                          "description": "Estados que se devuelven. Por defecto, todos menos APROBADA."
                        },
                        "desdePeriodo": {
                          "type": "string", "pattern": "%2$s",
                          "description": "Mes AAAA-MM desde el que se devuelven planillas (incluido)."
                        },
                        "limite": {
                          "type": "integer", "minimum": 1, "maximum": 50, "default": 30,
                          "description": "Planillas máximas."
                        }
                      },
                      "additionalProperties": false
                    }""".formatted(UUID, PERIODO));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private EsquemasEntradaMcp() {
    }

    /** Esquema de entrada de la herramienta; IllegalStateException si no tiene (un error al programar). */
    static Map<String, Object> de(String herramienta) {
        String esquema = ESQUEMAS.get(herramienta);
        if (esquema == null) {
            throw new IllegalStateException("La herramienta " + herramienta + " no tiene esquema de entrada");
        }
        return JSON.readValue(esquema, new TypeReference<Map<String, Object>>() {
        });
    }
}
