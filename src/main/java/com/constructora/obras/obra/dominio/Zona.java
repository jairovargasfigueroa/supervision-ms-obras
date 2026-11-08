package com.constructora.obras.obra.dominio;

/**
 * Zona de la obra (401, decisión del equipo): opcional; se usa en los reportes que cruzan servicios. Una obra sin zona
 * la tiene en null («Sin zona» en el ERP).
 */
public enum Zona {
    NORTE,
    SUR,
    ESTE,
    OESTE,
    CENTRO
}
