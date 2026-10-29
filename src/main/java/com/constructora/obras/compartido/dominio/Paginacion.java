package com.constructora.obras.compartido.dominio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import java.util.List;
import java.util.Set;

/**
 * Página pedida de una lista (convenciones, «Listas y paginación»): pagina desde 1 (por defecto 1), tamano de 1 a
 * 100 (por defecto 20), orden (uno de los campos que admite la lista) y dirección. Un valor fuera de rango responde
 * 400 PAGINACION_INVALIDA.
 */
public record Paginacion(int pagina, int tamano, String orden, boolean ascendente) {

    public static final String PAGINACION_INVALIDA = "PAGINACION_INVALIDA";
    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    /**
     * Arma la página con los valores por defecto de los que no vienen.
     *
     * @param ordenes         campos por los que la lista se puede ordenar
     * @param ordenPorDefecto campo si no viene orden
     * @param direccion       asc o desc; si no viene, desc
     */
    public static Paginacion de(Integer pagina, Integer tamano, String orden, String direccion, Set<String> ordenes,
            String ordenPorDefecto) {
        int numero = pagina == null ? 1 : pagina;
        int cantidad = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        String campo = orden == null || orden.isBlank() ? ordenPorDefecto : orden;
        String sentido = direccion == null || direccion.isBlank() ? "desc" : direccion;
        if (numero < 1) {
            throw invalida("pagina", "Debe ser 1 o más");
        }
        if (cantidad < 1 || cantidad > TAMANO_MAXIMO) {
            throw invalida("tamano", "Debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        if (!ordenes.contains(campo)) {
            throw invalida("orden", "Debe ser uno de " + String.join(", ", ordenes));
        }
        if (!sentido.equals("asc") && !sentido.equals("desc")) {
            throw invalida("direccion", "Debe ser asc o desc");
        }
        return new Paginacion(numero, cantidad, campo, sentido.equals("asc"));
    }

    private static ReglaNegocioException invalida(String campo, String mensaje) {
        return new ReglaNegocioException(PAGINACION_INVALIDA, "No se pudo cargar la lista.",
                List.of(new ErrorCampo(campo, mensaje)));
    }
}
