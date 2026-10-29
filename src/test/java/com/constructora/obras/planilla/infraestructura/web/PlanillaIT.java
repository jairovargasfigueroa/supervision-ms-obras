package com.constructora.obras.planilla.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 007 · Elaborar y presentar planilla, pruebas de API de la spec: planilla abierta (409), periodo futuro y no posterior
 * (422), versión desactualizada (409), fotos publicadas al presentar, más roles, límite, subtotal 0 y lo que ve el
 * cliente. Obra con P-01 Losa 10 m2 × 100 y P-02 Muro 20 m2 × 50 (contrato Bs 2.000, anticipo 10 %, retención 7 %).
 * Los periodos se toman respecto del mes actual en la zona del negocio.
 */
class PlanillaIT extends PruebaIntegracion {

    private static final YearMonth ACTUAL = YearMonth.now(Reloj.ZONA_NEGOCIO);
    private static final String MES = ACTUAL.toString();
    private static final String MES_ANTERIOR = ACTUAL.minusMonths(1).toString();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private Equipo equipo;
    private UUID obra;
    private UUID losa;
    private UUID muro;

    @BeforeEach
    void obraEnEjecucion() throws Exception {
        equipo = Equipo.nuevo();
        obra = ObrasPrueba.registrarDosPartidas(mvc, "Edificio Planillas", equipo);
        PlanillasPrueba.cambiarEstado(jdbc, obra, "EN_EJECUCION");
        losa = PlanillasPrueba.partida(jdbc, obra, "P-01");
        muro = PlanillasPrueba.partida(jdbc, obra, "P-02");
    }

    @Test
    void elResidenteCreaLaPlanillaEnBorradorConUnaLineaPorPartidaEnCero() throws Exception {
        crear(obra, MES, residente())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/planillas/")))
                .andExpect(jsonPath("$.obraId").value(obra.toString()))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.periodo").value(MES))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.lineas.length()").value(2))
                .andExpect(jsonPath("$.lineas[0].codigo").value("P-01"))
                .andExpect(jsonPath("$.lineas[0].partidaId").value(losa.toString()))
                .andExpect(jsonPath("$.lineas[0].nombre").value("Losa"))
                .andExpect(jsonPath("$.lineas[0].unidad").value("m2"))
                .andExpect(jsonPath("$.lineas[0].precioUnitario").value(100))
                .andExpect(jsonPath("$.lineas[0].cantidadContratada").value(10))
                .andExpect(jsonPath("$.lineas[0].acumuladoAnterior").value(0))
                .andExpect(jsonPath("$.lineas[0].cantidadPeriodo").value(0))
                .andExpect(jsonPath("$.lineas[0].saldo").value(10))
                .andExpect(jsonPath("$.lineas[1].codigo").value("P-02"))
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.neto").value(0))
                .andExpect(jsonPath("$.anticipoPct").value(10))
                .andExpect(jsonPath("$.retencionPct").value(7))
                .andExpect(jsonPath("$.observacion").isEmpty())
                .andExpect(jsonPath("$.cobroId").isEmpty())
                .andExpect(jsonPath("$.fotoIds.length()").value(0));
    }

    @Test
    void numeroSiguienteYAcumuladoAnteriorDeLasAprobadas() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, ACTUAL.minusMonths(2).toString(), "APROBADA",
                Map.of(losa, new BigDecimal("4")));

        crear(obra, MES_ANTERIOR, residente())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.lineas[0].acumuladoAnterior").value(4))
                .andExpect(jsonPath("$.lineas[0].acumuladoTotal").value(4))
                .andExpect(jsonPath("$.lineas[0].saldo").value(6));
    }

    /** 008 · El número del cobro que Obras guarda al aprobarse sale en el detalle y en la lista. */
    @Test
    void elDetalleYLaListaTraenElCobroDeUnaAprobada() throws Exception {
        UUID aprobada = PlanillasPrueba.insertar(jdbc, obra, 1, MES_ANTERIOR, "APROBADA", Map.of(losa, BigDecimal.ONE));
        UUID cobroId = UUID.randomUUID();
        jdbc.update("update planilla set cobro_id = ?, cobro_numero = 'COB-2026-0007' where id = ?", cobroId,
                aprobada);

        mvc.perform(conToken(get("/planillas/{id}", aprobada), cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobroId").value(cobroId.toString()))
                .andExpect(jsonPath("$.cobroNumero").value("COB-2026-0007"));
        mvc.perform(conToken(get("/obras/{id}/planillas", obra), residente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cobroId").value(cobroId.toString()))
                .andExpect(jsonPath("$[0].cobroNumero").value("COB-2026-0007"));
    }

    @Test
    void conUnaPlanillaSinAprobarNoSeCreaOtra() throws Exception {
        crear(obra, MES_ANTERIOR, residente()).andExpect(status().isCreated());

        crear(obra, MES, residente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_ABIERTA_EXISTE"))
                .andExpect(jsonPath("$.detail")
                        .value("Ya hay una planilla sin aprobar en esta obra. Termínala antes de crear otra."));
    }

    @Test
    void elPeriodoDebeSerPosteriorAlDeLaUltimaPlanilla() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, MES_ANTERIOR, "APROBADA", Map.of());

        for (String periodo : new String[] {MES_ANTERIOR, ACTUAL.minusMonths(3).toString()}) {
            crear(obra, periodo, residente())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("PERIODO_NO_POSTERIOR"))
                    .andExpect(jsonPath("$.detail").value(
                            "El periodo debe ser posterior al de la última planilla (" + MES_ANTERIOR + ")."))
                    .andExpect(jsonPath("$.errores[0].campo").value("periodo"));
        }
        crear(obra, MES, residente()).andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2));
    }

    @Test
    void noSeCreaLaPlanillaDeUnMesFuturo() throws Exception {
        crear(obra, ACTUAL.plusMonths(1).toString(), residente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_FUTURO"))
                .andExpect(jsonPath("$.detail").value("No se puede crear la planilla de un mes que todavía no llegó."));
    }

    @Test
    void laObraDebeEstarEnEjecucion() throws Exception {
        PlanillasPrueba.cambiarEstado(jdbc, obra, "SIN_CONTRATO");

        crear(obra, MES, residente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_EN_EJECUCION"));
    }

    @Test
    void soloElResidenteAsignadoCreaYElPeriodoDebeSerAaaaMm() throws Exception {
        String maestro = TokensPrueba.valido(equipo.maestro().toString(), "maestro");
        // El rol se revisa antes que el cuerpo
        mvc.perform(post("/obras/{id}/planillas", obra).header("Authorization", "Bearer " + maestro)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        crear(obra, MES, TokensPrueba.valido(ObrasPrueba.ADMIN, "admin"))
                .andExpect(status().isForbidden());
        crear(obra, "2026-13", residente())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("periodo"));
        crear(obra, MES, TokensPrueba.valido(UUID.randomUUID().toString(), "residente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        // Ve la obra como maestro, no como su residente
        crear(obra, MES, TokensPrueba.valido(equipo.maestro().toString(), "maestro", "residente"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void guardarRecalculaTotalesYSubeLaVersion() throws Exception {
        UUID planilla = crearYLeerId(MES);

        // Losa 2,5 × 100 = 250,00 · Muro 3,333 × 50 = 166,65 → 416,65 · anticipo 41,665 → 41,67 · retención
        // 29,1655 → 29,17 · neto 345,81
        guardar(planilla, 0, lineas(losa, "2.5", muro, "3.333"), "", residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lineas[0].cantidadPeriodo").value(2.5))
                .andExpect(jsonPath("$.lineas[0].monto").value(250))
                .andExpect(jsonPath("$.lineas[0].acumuladoTotal").value(2.5))
                .andExpect(jsonPath("$.lineas[0].saldo").value(7.5))
                .andExpect(jsonPath("$.lineas[1].monto").value(166.65))
                .andExpect(jsonPath("$.subtotal").value(416.65))
                .andExpect(jsonPath("$.descuentoAnticipo").value(41.67))
                .andExpect(jsonPath("$.retencion").value(29.17))
                .andExpect(jsonPath("$.neto").value(345.81));
        mvc.perform(conToken(get("/planillas/{id}", planilla), admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.neto").value(345.81));
        mvc.perform(conToken(get("/obras/{id}/planillas", obra), admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planilla.toString()))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].periodo").value(MES))
                .andExpect(jsonPath("$[0].estado").value("BORRADOR"))
                .andExpect(jsonPath("$[0].neto").value(345.81))
                .andExpect(jsonPath("$[0].cobroNumero").isEmpty());
    }

    @Test
    void conUnaVersionDistintaSeAvisaQueLaPlanillaCambio() throws Exception {
        UUID planilla = crearYLeerId(MES);
        guardar(planilla, 0, lineas(losa, "1"), "", residente()).andExpect(status().isOk());

        guardar(planilla, 0, lineas(losa, "2"), "", residente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSION_DESACTUALIZADA"))
                .andExpect(jsonPath("$.detail").value("La planilla cambió, recarga para ver la última versión"));
        presentar(planilla, 0, residente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSION_DESACTUALIZADA"));
        mvc.perform(conToken(get("/planillas/{id}", planilla), residente()))
                .andExpect(jsonPath("$.lineas[0].cantidadPeriodo").value(1));
    }

    @Test
    void superarLoContratadoSeRechazaPorPartida() throws Exception {
        UUID planilla = crearYLeerId(MES);

        guardar(planilla, 0, lineas(losa, "10", muro, "20.001"), "", residente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SUPERA_CANTIDAD_CONTRATADA"))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value(muro.toString()))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Supera lo contratado (saldo: 20)"));
        guardar(planilla, 0, lineas(losa, "10", muro, "20"), "", residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(2000));
        guardar(planilla, 1, lineas(losa, "-1"), "", residente())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void soloSeAdjuntanFotosDeLaObraDelPeriodo() throws Exception {
        UUID planilla = crearYLeerId(MES);
        UUID delMes = foto(obra, ACTUAL.atDay(15));
        UUID delMesAnterior = foto(obra, ACTUAL.minusMonths(1).atDay(15));
        UUID otraObra = ObrasPrueba.registrar(mvc, "Otra obra", Equipo.nuevo());
        UUID ajena = foto(otraObra, ACTUAL.atDay(15));

        guardar(planilla, 0, lineas(losa, "1"), "\"" + delMesAnterior + "\", \"" + ajena + "\"", residente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FOTO_FUERA_DEL_PERIODO"))
                .andExpect(jsonPath("$.errores[0].campo").value(delMesAnterior.toString()))
                .andExpect(jsonPath("$.errores[1].campo").value(ajena.toString()));
        guardar(planilla, 0, lineas(losa, "1"), "\"" + delMes + "\"", residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoIds.length()").value(1))
                .andExpect(jsonPath("$.fotoIds[0]").value(delMes.toString()));
    }

    @Test
    void presentarLaPasaAPresentadaYPublicaSusFotos() throws Exception {
        UUID planilla = crearYLeerId(MES);
        UUID adjunta = foto(obra, ACTUAL.atDay(1));
        UUID sinAdjuntar = foto(obra, ACTUAL.atDay(2));
        guardar(planilla, 0, lineas(losa, "1"), "\"" + adjunta + "\"", residente()).andExpect(status().isOk());

        presentar(planilla, 1, residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESENTADA"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.fotoIds[0]").value(adjunta.toString()));

        assertThat(publicada(adjunta)).isTrue();
        assertThat(publicada(sinAdjuntar)).isFalse();
        // El cliente desde el portal ve la planilla presentada y solo la foto publicada
        mvc.perform(conToken(get("/obras/{id}/fotos", obra), cliente()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(adjunta.toString()));
        mvc.perform(conToken(get("/planillas/{id}", planilla), cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESENTADA"));
        // Ya no se edita ni se vuelve a presentar
        guardar(planilla, 2, lineas(losa, "2"), "", residente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_EDITABLE"))
                .andExpect(jsonPath("$.detail").value("La planilla ya no se puede editar (estado PRESENTADA)"));
        presentar(planilla, 2, residente())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_EDITABLE"));
    }

    @Test
    void conSubtotalCeroNoSePresenta() throws Exception {
        UUID planilla = crearYLeerId(MES);

        presentar(planilla, 0, residente())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SUBTOTAL_CERO"));
    }

    @Test
    void unaObservadaSeCorrigeYSeVuelveAPresentar() throws Exception {
        UUID planilla = crearYLeerId(MES);
        guardar(planilla, 0, lineas(losa, "1"), "", residente()).andExpect(status().isOk());
        jdbc.update("update planilla set estado = 'OBSERVADA', observacion = 'Falta el muro' where id = ?", planilla);

        mvc.perform(conToken(get("/planillas/{id}", planilla), residente()))
                .andExpect(jsonPath("$.observacion").value("Falta el muro"));
        guardar(planilla, 1, lineas(muro, "2"), "", residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(200));
        presentar(planilla, 2, residente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESENTADA"));
    }

    @Test
    void adminYGerenteLeenPeroNoElaboran() throws Exception {
        UUID planilla = crearYLeerId(MES);
        String gerente = TokensPrueba.valido(UUID.randomUUID().toString(), "gerente");

        mvc.perform(conToken(get("/planillas/{id}", planilla), gerente)).andExpect(status().isOk());
        guardar(planilla, 0, lineas(losa, "1"), "", gerente)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        presentar(planilla, 0, admin()).andExpect(status().isForbidden());
        guardar(planilla, 0, lineas(losa, "1"), "", TokensPrueba.valido(UUID.randomUUID().toString(), "residente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
        mvc.perform(conToken(get("/planillas/{id}", "no-es-uuid"), admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
    }

    @Test
    void elClienteDesdeElPortalNoVeBorradores() throws Exception {
        PlanillasPrueba.insertar(jdbc, obra, 1, MES_ANTERIOR, "APROBADA", Map.of(losa, BigDecimal.ONE));
        UUID borrador = crearYLeerId(MES);

        mvc.perform(conToken(get("/obras/{id}/planillas", obra), cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("APROBADA"));
        mvc.perform(conToken(get("/planillas/{id}", borrador), cliente()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLANILLA_NO_ENCONTRADA"));
        mvc.perform(conToken(get("/obras/{id}/planillas", obra), residente()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].estado").value("BORRADOR"));
        // GET /obras: la última planilla del cliente salta el borrador; la del residente lo muestra
        mvc.perform(conToken(get("/obras"), cliente()))
                .andExpect(jsonPath("$[0].ultimaPlanilla.periodo").value(MES_ANTERIOR))
                .andExpect(jsonPath("$[0].ultimaPlanilla.estado").value("APROBADA"));
        mvc.perform(conToken(get("/obras"), residente()))
                .andExpect(jsonPath("$[0].ultimaPlanilla.periodo").value(MES))
                .andExpect(jsonPath("$[0].ultimaPlanilla.estado").value("BORRADOR"));
    }

    // --- ayudas ---

    private String residente() {
        return TokensPrueba.valido(equipo.residente().toString(), "residente");
    }

    private String cliente() {
        return TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente");
    }

    private static String admin() {
        return TokensPrueba.valido(ObrasPrueba.ADMIN, "admin");
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder pedido, String token) {
        return pedido.header("Authorization", "Bearer " + token);
    }

    private ResultActions crear(UUID obraId, String periodo, String token) throws Exception {
        return mvc.perform(conToken(post("/obras/{id}/planillas", obraId), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"periodo\": \"" + periodo + "\"}"));
    }

    private UUID crearYLeerId(String periodo) throws Exception {
        String respuesta = crear(obra, periodo, residente()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(respuesta, "$.id"));
    }

    private ResultActions guardar(UUID planilla, int version, String lineas, String fotoIds, String token)
            throws Exception {
        return mvc.perform(conToken(put("/planillas/{id}", planilla), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": " + version + ", \"lineas\": [" + lineas + "], \"fotoIds\": [" + fotoIds
                        + "]}"));
    }

    private ResultActions presentar(UUID planilla, int version, String token) throws Exception {
        return mvc.perform(conToken(post("/planillas/{id}/presentar", planilla), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\": " + version + "}"));
    }

    private static String lineas(Object... partidaYCantidad) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < partidaYCantidad.length; i += 2) {
            if (i > 0) {
                texto.append(", ");
            }
            texto.append("{\"partidaId\": \"").append(partidaYCantidad[i]).append("\", \"cantidadPeriodo\": ")
                    .append(partidaYCantidad[i + 1]).append('}');
        }
        return texto.toString();
    }

    /** Foto de la losa tomada al mediodía de ese día en La Paz. */
    private UUID foto(UUID obraId, LocalDate dia) {
        UUID partida = PlanillasPrueba.partida(jdbc, obraId, "P-01");
        Instant tomada = dia.atTime(12, 0).atZone(Reloj.ZONA_NEGOCIO).toInstant();
        return PlanillasPrueba.insertarFoto(jdbc, obraId, partida, tomada);
    }

    private boolean publicada(UUID foto) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select publicada_cliente from foto_avance where id = ?",
                Boolean.class, foto));
    }
}
