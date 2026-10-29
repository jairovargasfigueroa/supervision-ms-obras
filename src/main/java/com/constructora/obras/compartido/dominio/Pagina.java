package com.constructora.obras.compartido.dominio;

import java.util.List;
import java.util.function.Function;

/** Una página de una lista: sus elementos y el total de la lista completa (convenciones, «Listas y paginación»). */
public record Pagina<T>(List<T> items, long total, int pagina, int tamano) {

    public Pagina {
        items = List.copyOf(items);
    }

    public <R> Pagina<R> map(Function<T, R> conversion) {
        return new Pagina<>(items.stream().map(conversion).toList(), total, pagina, tamano);
    }
}
