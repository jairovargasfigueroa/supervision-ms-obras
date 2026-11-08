package com.constructora.obras.avance.dominio;

/**
 * 301 · Detección de casco y chaleco hecha en el celular sobre la foto de avance: cuántas personas aparecen, cuántas
 * sin casco, cuántas sin chaleco y la versión del modelo usada (epp-1.0.0). Es un dato de apoyo: si no es válida, la
 * foto se registra igual sin ella y con el aviso DETECCION_DESCARTADA (diseño de la 301, «Obras»).
 */
public record DeteccionEpp(int personas, int sinCasco, int sinChaleco, String versionModelo) {

    /** Aviso de la respuesta de POST /obras/{obraId}/fotos cuando la detección recibida no es válida. */
    public static final String DETECCION_DESCARTADA = "DETECCION_DESCARTADA";
    /** Largo máximo de versionModelo (columna version_modelo_epp varchar(40)). */
    public static final int LARGO_MAXIMO_VERSION = 40;

    public DeteccionEpp {
        String problema = problema(personas, sinCasco, sinChaleco, versionModelo);
        if (problema != null) {
            throw new IllegalArgumentException(problema);
        }
    }

    /**
     * Valida la detección tal como llegó. Un valor null es un campo que falta o que no es del tipo esperado (por
     * ejemplo, un número con decimales).
     */
    public static Validacion validar(Integer personas, Integer sinCasco, Integer sinChaleco, String versionModelo) {
        if (personas == null || sinCasco == null || sinChaleco == null || versionModelo == null) {
            return new Validacion(null, "faltan campos o no son del tipo esperado");
        }
        String problema = problema(personas, sinCasco, sinChaleco, versionModelo);
        return problema == null
                ? new Validacion(new DeteccionEpp(personas, sinCasco, sinChaleco, versionModelo), null)
                : new Validacion(null, problema);
    }

    /** null si cumple: enteros ≥ 0, sinCasco y sinChaleco ≤ personas, versionModelo de 1 a 40 caracteres. */
    private static String problema(int personas, int sinCasco, int sinChaleco, String versionModelo) {
        if (personas < 0 || sinCasco < 0 || sinChaleco < 0) {
            return "los conteos deben ser enteros mayores o iguales a 0";
        }
        if (sinCasco > personas) {
            return "sinCasco es mayor que personas";
        }
        if (sinChaleco > personas) {
            return "sinChaleco es mayor que personas";
        }
        if (versionModelo == null || versionModelo.isBlank() || versionModelo.length() > LARGO_MAXIMO_VERSION) {
            return "versionModelo debe tener de 1 a " + LARGO_MAXIMO_VERSION + " caracteres";
        }
        return null;
    }

    /**
     * Resultado de validar: la detección válida, o null con el motivo por el que se descarta (para el registro).
     */
    public record Validacion(DeteccionEpp deteccion, String motivo) {

        public boolean descartada() {
            return deteccion == null;
        }

        public Validacion {
            if ((deteccion == null) == (motivo == null)) {
                throw new IllegalArgumentException("Se espera la detección o el motivo, no ambos");
            }
        }
    }
}
