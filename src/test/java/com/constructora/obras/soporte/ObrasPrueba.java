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

    /** Registra la obra y devuelve su id. */
    public static UUID registrar(MockMvc mvc, String nombre, Equipo equipo) throws Exception {
        String respuesta = mvc.perform(post("/obras")
                        .header("Authorization", "Bearer " + TokensPrueba.valido(ADMIN, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, equipo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(respuesta, "$.id"));
    }
}
