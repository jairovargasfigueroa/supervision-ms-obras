package com.constructora.obras.diario.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PlanillasPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 103 · Pruebas de la spec (diseño, «Pruebas», Obras API): mismo id dos veces, otro id el mismo día (también a la
 * vez), maestro no asignado, residente asignado, obra no en ejecución, fechas, formato, GET por rol y rango por
 * defecto de 30 días. Con el reloj real: hoy en La Paz.
 */
class DiarioIT extends PruebaIntegracion {

    private static final String TEXTO = "Se vació la losa del 2do piso. Lluvia por la tarde, se cubrió con nylon.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoy = LocalDate.now(Reloj.ZONA_NEGOCIO);
    private Equipo equipo;
    private UUID obraId;
    private String maestro;

    @BeforeEach
    void obraEnEjecucion() throws Exception {
        equipo = Equipo.nuevo();
        obraId = ObrasPrueba.registrar(mvc, "Obra con diario", equipo);
        PlanillasPrueba.cambiarEstado(jdbc, obraId, "EN_EJECUCION");
        PlanillasPrueba.cambiarInicio(jdbc, obraId, hoy.minusDays(60));
        maestro = TokensPrueba.desde("app-obra", equipo.maestro().toString(), "maestro");
    }

    @Test
    void elMaestroRegistraYLaListaLaDevuelve() throws Exception {
        UUID id = UUID.randomUUID();

        registrar(obraId, cuerpo(id, hoy, "LLUVIA_LEVE", TEXTO), maestro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.obraId").value(obraId.toString()))
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.clima").value("LLUVIA_LEVE"))
                .andExpect(jsonPath("$.texto").value(TEXTO))
                .andExpect(jsonPath("$.registradoPor").value(equipo.maestro().toString()))
                .andExpect(jsonPath("$.registradoEn").isString());

        listar(obraId, "", maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].obraId").value(obraId.toString()))
                .andExpect(jsonPath("$[0].registradoPor").value(equipo.maestro().toString()));
    }

    @Test
    void mismoIdDosVecesEsUnaSolaFilaY200ConLaGuardadaAunqueElCuerpoCambie() throws Exception {
        UUID id = UUID.randomUUID();

        String creada = registrar(obraId, cuerpo(id, hoy, "LLUVIA_LEVE", TEXTO), maestro)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String repetida = registrar(obraId, cuerpo(id, hoy, "LLUVIA_LEVE", TEXTO), maestro)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String otroCuerpo = registrar(obraId, cuerpo(id, hoy.minusDays(1), "SOLEADO", "Otro texto del día"),
                maestro)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(repetida).isEqualTo(creada);
        assertThat(otroCuerpo).isEqualTo(creada);
        assertThat(filas(obraId)).isEqualTo(1);
    }

    @Test
    void otroIdElMismoDiaEsDiaYaRegistrado() throws Exception {
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO), maestro).andExpect(status().isCreated());

        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "NUBLADO", TEXTO), maestro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DIARIO_DIA_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Ya existe una entrada del diario para ese día"));
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy.minusDays(1), "NUBLADO", TEXTO), maestro)
                .andExpect(status().isCreated());
        assertThat(filas(obraId)).isEqualTo(2);
    }

    @Test
    void variasDelMismoDiaALaVezGuardanUnaYLasDemasSon409() throws Exception {
        int pedidos = 6;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(pedidos);
        try {
            List<Future<Integer>> estados = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                String cuerpo = cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO);
                Callable<Integer> pedido = () -> {
                    largada.await();
                    return registrar(obraId, cuerpo, maestro).andReturn().getResponse().getStatus();
                };
                estados.add(hilos.submit(pedido));
            }
            largada.countDown();
            List<Integer> resultado = new ArrayList<>();
            for (Future<Integer> estado : estados) {
                resultado.add(estado.get());
            }
            assertThat(resultado).containsOnly(201, 409);
            assertThat(resultado).filteredOn(estado -> estado == 201).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(filas(obraId)).isEqualTo(1);
    }

    @Test
    void maestroNoAsignadoRecibeObraNoEncontrada() throws Exception {
        String otroMaestro = TokensPrueba.desde("app-obra", UUID.randomUUID().toString(), "maestro");
        String cuerpo = cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO);

        registrar(obraId, cuerpo, otroMaestro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        registrar(UUID.randomUUID(), cuerpo, maestro)
                .andExpect(status().isNotFound());
        mvc.perform(post("/obras/no-es-uuid/diario").header("Authorization", "Bearer " + maestro)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        assertThat(filas(obraId)).isZero();
    }

    @Test
    void elResidenteAsignadoRecibeSinPermisoAntesQueElCuerpo() throws Exception {
        String residente = TokensPrueba.valido(equipo.residente().toString(), "residente");
        // Con los roles maestro y residente, asignado solo como residente: ve la obra con otro papel
        String residenteConRolMaestro = TokensPrueba.desde("app-obra", equipo.residente().toString(), "maestro",
                "residente");

        registrar(obraId, "{}", residente)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO), residenteConRolMaestro)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        assertThat(filas(obraId)).isZero();
    }

    @Test
    void obraNoEnEjecucion() throws Exception {
        PlanillasPrueba.cambiarEstado(jdbc, obraId, "SIN_CONTRATO");

        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO), maestro)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_EN_EJECUCION"))
                .andExpect(jsonPath("$.detail").value("La obra no está en ejecución"));
    }

    @Test
    void fechaFuturaOAnteriorAlInicioDeLaObra() throws Exception {
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy.plusDays(1), "SOLEADO", TEXTO), maestro)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_DIARIO_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La fecha no es válida para esta obra"))
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy.minusDays(61), "SOLEADO", TEXTO), maestro)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_DIARIO_INVALIDA"));
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy.minusDays(60), "SOLEADO", TEXTO), maestro)
                .andExpect(status().isCreated());
    }

    @Test
    void formatoDelCuerpo() throws Exception {
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "GRANIZO", "corto"), maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("clima"))
                .andExpect(jsonPath("$.errores[1].campo").value("texto"));
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", "a".repeat(2001)), maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("texto"));
        String sinId = """
                { "fecha": "%s", "clima": "SOLEADO", "texto": "%s" }""".formatted(hoy, TEXTO);
        registrar(obraId, sinId, maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("id"));
        // El texto se guarda sin los espacios de los extremos
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", "   " + "a".repeat(10) + "  "), maestro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("a".repeat(10)));
    }

    @Test
    void getPorRol() throws Exception {
        registrar(obraId, cuerpo(UUID.randomUUID(), hoy, "SOLEADO", TEXTO), maestro).andExpect(status().isCreated());

        for (String token : List.of(TokensPrueba.valido(equipo.residente().toString(), "residente"),
                TokensPrueba.valido(UUID.randomUUID().toString(), "admin"),
                TokensPrueba.valido(UUID.randomUUID().toString(), "gerente"), maestro)) {
            listar(obraId, "", token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        }
        listar(obraId, "", TokensPrueba.desde("portal", equipo.cliente().toString(), "cliente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OBRA_NO_ENCONTRADA"));
        listar(obraId, "", TokensPrueba.desde("app-obra", UUID.randomUUID().toString(), "maestro"))
                .andExpect(status().isNotFound());
        listar(obraId, "", TokensPrueba.desde("app-obra", equipo.almacenero().toString(), "almacenero"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void rangoPorDefectoDeTreintaDiasYRangoPedido() throws Exception {
        for (int dias : new int[] {30, 29, 5, 0}) {
            insertar(obraId, hoy.minusDays(dias));
        }

        listar(obraId, "", maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fecha").value(hoy.toString()))
                .andExpect(jsonPath("$[1].fecha").value(hoy.minusDays(5).toString()))
                .andExpect(jsonPath("$[2].fecha").value(hoy.minusDays(29).toString()));
        listar(obraId, "?desde=" + hoy.minusDays(30) + "&hasta=" + hoy.minusDays(5), maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].fecha").value(hoy.minusDays(30).toString()));
        listar(obraId, "?desde=" + hoy.minusDays(366) + "&hasta=" + hoy, maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));
        listar(obraId, "?desde=" + hoy.minusDays(365) + "&hasta=" + hoy, maestro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        listar(obraId, "?desde=ayer", maestro)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    private int filas(UUID obra) {
        return jdbc.queryForObject("select count(*) from entrada_diario where obra_id = ?", Integer.class, obra);
    }

    private void insertar(UUID obra, LocalDate fecha) {
        jdbc.update("""
                insert into entrada_diario (id, obra_id, fecha, clima, texto, registrado_por, registrado_en)
                values (?, ?, ?, 'NUBLADO', 'Texto del día', ?, now())""", UUID.randomUUID(), obra, fecha,
                equipo.maestro());
    }

    ResultActions registrar(UUID obra, String cuerpo, String token) throws Exception {
        return mvc.perform(post("/obras/" + obra + "/diario").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    ResultActions listar(UUID obra, String consulta, String token) throws Exception {
        return mvc.perform(get("/obras/" + obra + "/diario" + consulta).header("Authorization", "Bearer " + token));
    }

    static String cuerpo(UUID id, LocalDate fecha, String clima, String texto) {
        return """
                { "id": "%s", "fecha": "%s", "clima": "%s", "texto": "%s" }""".formatted(id, fecha, clima, texto);
    }
}
