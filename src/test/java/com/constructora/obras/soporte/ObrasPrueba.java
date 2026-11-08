package com.constructora.obras.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registra obras de prueba por la API (POST /obras como admin) con asignaciones elegidas por la prueba. */
public final class ObrasPrueba {

    public static final String ADMIN = "a0000000-0000-4000-8000-000000000001";

    private ObrasPrueba() {
    }

    /** Asignaciones nuevas (ids al azar) para que cada prueba tenga sus propios usuarios. */
    public record Equipo(UUID residente, UUID maestro, UUID almacenero, UUID cliente) {

        public static Equipo nuevo() {
            return new Equipo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }
    }

    /** Cuerpo válido: una partida de 10 × 100 = Bs 1.000,00, igual al monto del contrato. */
    public static String cuerpo(String nombre, Equipo equipo) {
        return """
                {
                  "nombre": "%s", "ubicacion": "Santa Cruz", "fechaInicio": "2026-11-01",
                  "asignaciones": { "residenteId": "%s", "maestroId": "%s", "almaceneroId": "%s", "clienteId": "%s" },
                  "contrato": { "monto": 1000, "plazoDias": 100, "anticipoPct": 10, "retencionPct": 7,
                                "firmadoEn": "2026-10-20" },
                  "partidas": [{ "codigo": "P-01", "nombre": "Losa", "unidad": "m2", "cantidadContratada": 10,
                                 "precioUnitario": 100, "insumos": [{ "materialId": "cemento", "cantidadPorUnidad": 1.1 }] }]
                }""".formatted(nombre, equipo.residente(), equipo.maestro(), equipo.almacenero(), equipo.cliente());
    }

    /**
     * Cuerpo válido con dos partidas (Bs 2.000,00, plazo de 100 días): P-01 Losa 10 m2 × 100 con cemento 1,1 y
     * fierro 2,5 por m2, y P-02 Muro 20 m2 × 50 con ladrillo 30 por m2.
     */
    public static String cuerpoDosPartidas(String nombre, Equipo equipo) {
        return """
                {
                  "nombre": "%s", "ubicacion": "Santa Cruz", "fechaInicio": "2026-11-01",
                  "asignaciones": { "residenteId": "%s", "maestroId": "%s", "almaceneroId": "%s", "clienteId": "%s" },
                  "contrato": { "monto": 2000, "plazoDias": 100, "anticipoPct": 10, "retencionPct": 7,
                                "firmadoEn": "2026-10-20" },
                  "partidas": [
                    { "codigo": "P-02", "nombre": "Muro", "unidad": "m2", "cantidadContratada": 20,
                      "precioUnitario": 50, "insumos": [{ "materialId": "ladrillo", "cantidadPorUnidad": 30 }] },
                    { "codigo": "P-01", "nombre": "Losa", "unidad": "m2", "cantidadContratada": 10,
                      "precioUnitario": 100, "insumos": [{ "materialId": "fierro", "cantidadPorUnidad": 2.5 },
                                                         { "materialId": "cemento", "cantidadPorUnidad": 1.1 }] }
                  ]
                }""".formatted(nombre, equipo.residente(), equipo.maestro(), equipo.almacenero(), equipo.cliente());
    }

    /** Registra la obra y devuelve su id. */
    public static UUID registrar(MockMvc mvc, String nombre, Equipo equipo) throws Exception {
        return registrarConCuerpo(mvc, cuerpo(nombre, equipo));
    }

    /** Registra la obra con dos partidas (cuerpoDosPartidas) y devuelve su id. */
    public static UUID registrarDosPartidas(MockMvc mvc, String nombre, Equipo equipo) throws Exception {
        return registrarConCuerpo(mvc, cuerpoDosPartidas(nombre, equipo));
    }

    private static UUID registrarConCuerpo(MockMvc mvc, String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/obras")
                        .header("Authorization", "Bearer " + TokensPrueba.valido(ADMIN, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(respuesta, "$.id"));
    }
}
