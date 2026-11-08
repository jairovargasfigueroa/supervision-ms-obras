package com.constructora.obras.obra.infraestructura.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tarea 5: POST /obras (002, criterios 2 a 7 y 10). */
class RegistrarObraIT extends PruebaIntegracion {

    static final String ADMIN = "a0000000-0000-4000-8000-000000000001";
    static final String RESIDENTE = "a0000000-0000-4000-8000-000000000003";
    static final String MAESTRO = "a0000000-0000-4000-8000-000000000004";
    static final String ALMACENERO = "a0000000-0000-4000-8000-000000000005";
    static final String CLIENTE = "a0000000-0000-4000-8000-000000000006";

    @Autowired
    private MockMvc mvc;

    /** Cuerpo válido del contrato de ejemplo: 850 × 420,50 + 1.785 × 500 = 1.249.925,00. */
    static String cuerpo(String nombre, String monto, String anticipo, String partidas) {
        return """
                {
                  "nombre": "%s",
                  "ubicacion": "Av. Banzer 4to anillo, Santa Cruz",
                  "fechaInicio": "2026-11-01",
                  "asignaciones": {
                    "residenteId": "%s", "maestroId": "%s", "almaceneroId": "%s", "clienteId": "%s"
                  },
                  "contrato": { "monto": %s, "plazoDias": 300, "anticipoPct": %s, "retencionPct": 7,
                                "firmadoEn": "2026-10-20" },
                  "partidas": %s
                }""".formatted(nombre, RESIDENTE, MAESTRO, ALMACENERO, CLIENTE, monto, anticipo, partidas);
    }

    static String cuerpoValido(String nombre) {
        return cuerpo(nombre, "1249925.00", "10", PARTIDAS_VALIDAS);
    }

    static final String PARTIDAS_VALIDAS = """
            [
              { "codigo": "P-01", "nombre": "Losa de hormigón armado", "unidad": "m2",
                "cantidadContratada": 850, "precioUnitario": 420.50, "meses": "nov-dic",
                "insumos": [{ "materialId": "cemento", "cantidadPorUnidad": 1.1 }] },
              { "codigo": "P-02", "nombre": "Muro de ladrillo", "unidad": "m2",
                "cantidadContratada": 1785, "precioUnitario": 500 }
            ]""";

    @Test
    void adminRegistraLaObraSinContrato() throws Exception {
        registrar(TokensPrueba.valido(ADMIN, "admin"), cuerpoValido("Edificio Las Palmas"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/obras/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nombre").value("Edificio Las Palmas"))
                .andExpect(jsonPath("$.estado").value("SIN_CONTRATO"))
                .andExpect(jsonPath("$.motivoRechazoContrato").isEmpty())
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-01"))
                .andExpect(jsonPath("$.asignaciones.residenteId").value(RESIDENTE))
                .andExpect(jsonPath("$.asignaciones.clienteId").value(CLIENTE))
                .andExpect(jsonPath("$.contrato.contratoId").isEmpty())
                .andExpect(jsonPath("$.contrato.monto").value(1249925.00))
                .andExpect(jsonPath("$.contrato.plazoDias").value(300))
                .andExpect(jsonPath("$.avancePct").value(0))
                .andExpect(jsonPath("$.enRiesgo").value(false))
                .andExpect(jsonPath("$.partidas.length()").value(2))
                .andExpect(jsonPath("$.partidas[0].codigo").value("P-01"))
                .andExpect(jsonPath("$.partidas[0].precioUnitario").value(420.5))
                .andExpect(jsonPath("$.partidas[0].ejecutadaAcumulada").value(0))
                .andExpect(jsonPath("$.partidas[0].avancePct").value(0))
                .andExpect(jsonPath("$.partidas[0].insumos[0].materialId").value("cemento"))
                .andExpect(jsonPath("$.partidas[1].insumos.length()").value(0));
    }

    @Test
    void soloAdminPuedeRegistrar() throws Exception {
        esperarSinPermiso(registrar(TokensPrueba.valido(RESIDENTE, "residente"), cuerpoValido("Obra X")));
        esperarSinPermiso(registrar(TokensPrueba.valido("u-gerente", "gerente"), cuerpoValido("Obra X")));
        // Admin que entra por el portal: solo cuenta su rol cliente (regla del azp)
        esperarSinPermiso(registrar(TokensPrueba.desde("portal", ADMIN, "admin", "cliente"), cuerpoValido("Obra X")));
        // El permiso se comprueba antes que el formato del cuerpo
        esperarSinPermiso(registrar(TokensPrueba.valido(RESIDENTE, "residente"), "{}"));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(post("/obras").contentType(MediaType.APPLICATION_JSON).content(cuerpoValido("Obra X")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void formatoInvalidoResponde400ConLosCampos() throws Exception {
        String sinNombreNiPartidas = cuerpo("", "1249925.00", "10", "[]");
        registrar(admin(), sinNombreNiPartidas)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("partidas")));

        String cantidadCero = cuerpo("Obra X", "100", "10", """
                [{ "codigo": "P-01", "nombre": "Losa", "unidad": "m2", "cantidadContratada": 0, "precioUnitario": 100,
                   "insumos": [{ "materialId": "cemento", "cantidadPorUnidad": -1 }] }]""");
        registrar(admin(), cantidadCero)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("partidas[0].cantidadContratada")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("partidas[0].insumos[0].cantidadPorUnidad")));

        String nombreLargo = cuerpoValido("x".repeat(151));
        registrar(admin(), nombreLargo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Máximo 150 caracteres"));
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        registrar(admin(), "{ \"nombre\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        registrar(admin(), cuerpoValido("Obra X").replace("\"2026-11-01\"", "\"1/11/2026\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void presupuestoQueNoCoincideResponde422() throws Exception {
        registrar(admin(), cuerpo("Obra X", "1250000.00", "10", PARTIDAS_VALIDAS))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_COINCIDE"))
                .andExpect(jsonPath("$.detail").value(
                        "El presupuesto (Bs 1.249.925,00) no coincide con el monto del contrato (Bs 1.250.000,00)"));
    }

    @Test
    void codigoDePartidaRepetidoResponde422() throws Exception {
        registrar(admin(), cuerpo("Obra X", "200", "10", """
                [{ "codigo": "P-01", "nombre": "A", "unidad": "m2", "cantidadContratada": 1, "precioUnitario": 100 },
                 { "codigo": "P-01", "nombre": "B", "unidad": "m2", "cantidadContratada": 1, "precioUnitario": 100 }]"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CODIGO_PARTIDA_REPETIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("partidas[1].codigo"));
    }

    @Test
    void insumoRepetidoResponde422() throws Exception {
        registrar(admin(), cuerpo("Obra X", "100", "10", """
                [{ "codigo": "P-01", "nombre": "A", "unidad": "m2", "cantidadContratada": 1, "precioUnitario": 100,
                   "insumos": [{ "materialId": "cemento", "cantidadPorUnidad": 1 },
                               { "materialId": "cemento", "cantidadPorUnidad": 2 }] }]"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("INSUMO_REPETIDO"))
                .andExpect(jsonPath("$.detail").value("La partida P-01 tiene el mismo material más de una vez."));
    }

    @Test
    void contratoInvalidoResponde422() throws Exception {
        registrar(admin(), cuerpo("Obra X", "1249925.00", "95", PARTIDAS_VALIDAS))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrato.retencionPct"));
        registrar(admin(), cuerpo("Obra X", "0", "10", PARTIDAS_VALIDAS))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrato.monto"));
    }

    private static String admin() {
        return TokensPrueba.valido(ADMIN, "admin");
    }

    /** 401, criterio 1: la zona se elige de la lista y es opcional. */
    @Test
    void zonaValidaSeGuardaYSeDevuelveEnElDetalleYLaLista() throws Exception {
        String respuesta = registrar(admin(), conZona(cuerpoValido("Obra con zona 401"), "\"NORTE\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zona").value("NORTE"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/obras/" + id).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona").value("NORTE"));
        mvc.perform(get("/obras").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].zona", contains("NORTE")));
    }

    @Test
    void sinZonaQuedaNullEnElDetalleYLaLista() throws Exception {
        String respuesta = registrar(admin(), cuerpoValido("Obra sin zona 401"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zona").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");
        registrar(admin(), conZona(cuerpoValido("Obra con zona null 401"), "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zona").value(nullValue()));

        mvc.perform(get("/obras/" + id).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona").value(nullValue()));
        mvc.perform(get("/obras").header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].zona", contains(nullValue())));
    }

    @Test
    void zonaFueraDeLaListaResponde400ConElCampo() throws Exception {
        for (String zona : new String[] {"\"NORESTE\"", "\"norte\"", "\"\""}) {
            registrar(admin(), conZona(cuerpoValido("Obra X"), zona))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores[0].campo").value("zona"));
        }
    }

    private static String conZona(String cuerpo, String zonaJson) {
        return cuerpo.replace("\"fechaInicio\"", "\"zona\": " + zonaJson + ", \"fechaInicio\"");
    }

    private ResultActions registrar(String token, String cuerpo) throws Exception {
        return mvc.perform(post("/obras").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static void esperarSinPermiso(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }
}
