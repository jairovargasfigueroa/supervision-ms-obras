package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.semilla.DatosSemilla.ObraSemilla;
import com.constructora.obras.semilla.DatosSemilla.PartidaSemilla;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 011 · semilla/obras.json tiene la parte de Obras de docs/datos-prueba.md (secciones 2, 6, 7 y 10). */
class DatosSemillaTest {

    private static final Pattern OBRA = Pattern.compile("b0000000-0000-4000-8000-00000000000[1-3]");
    private static final Pattern PARTIDA = Pattern.compile("b1000000-0000-4000-8000-000000000([1-3])(\\d{2})");
    private static final Pattern PLANILLA = Pattern.compile("d0000000-0000-4000-8000-000000000([1-3])\\d{2}");
    private static final Pattern FOTO = Pattern.compile("f1000000-0000-4000-8000-0000000([1-3])(\\d{2})\\d{2}");
    private static final Pattern MATERIAL = Pattern.compile("e0000000-0000-4000-8000-0000000000\\d{2}");

    private final DatosSemilla datos = DatosSemilla.leer();

    @Test
    void cantidades() {
        assertThat(datos.obras()).hasSize(3);
        // 401: zona de cada obra (docs/datos-prueba.md, sección 6)
        assertThat(datos.obras()).extracting(ObraSemilla::zona).containsExactly("NORTE", "ESTE", "SUR");
        assertThat(datos.obras().stream().mapToInt(obra -> obra.partidas().size()).sum()).isEqualTo(21);
        assertThat(datos.planillas()).hasSize(5);
        assertThat(datos.fotos()).hasSize(10);
        assertThat(datos.planillas().stream().mapToInt(planilla -> planilla.fotos().size()).sum()).isEqualTo(5);
        assertThat(datos.fotos().stream().filter(DatosSemilla.FotoSemilla::publicadaCliente)).hasSize(5);
    }

    @Test
    void elPresupuestoDeCadaObraEsElMontoDelContrato() {
        assertThat(presupuesto(datos.obras().get(0))).isEqualByComparingTo("2773800.00");
        assertThat(presupuesto(datos.obras().get(1))).isEqualByComparingTo("3185140.00");
        assertThat(presupuesto(datos.obras().get(2))).isEqualByComparingTo("1900140.00");
        datos.obras().forEach(obra -> assertThat(presupuesto(obra)).isEqualByComparingTo(obra.contrato().monto()));
    }

    @Test
    void losIdsSiguenLosPatronesDelDocumento() {
        for (ObraSemilla obra : datos.obras()) {
            assertThat(obra.id().toString()).matches(OBRA);
            String numeroObra = obra.id().toString().substring(35);
            assertThat(obra.contratoId().toString()).isEqualTo("c0000000-0000-4000-8000-00000000000" + numeroObra);
            List<PartidaSemilla> partidas = obra.partidas();
            for (int i = 0; i < partidas.size(); i++) {
                PartidaSemilla partida = partidas.get(i);
                String pp = "%02d".formatted(i + 1);
                assertThat(partida.codigo()).isEqualTo("P-" + pp);
                assertThat(partida.id().toString()).isEqualTo("b1000000-0000-4000-8000-000000000" + numeroObra + pp);
            }
        }
        datos.planillas().forEach(planilla -> {
            assertThat(planilla.id().toString()).matches(PLANILLA);
            if (planilla.cobro() != null) {
                assertThat(planilla.cobro().id().toString())
                        .isEqualTo("c2" + planilla.id().toString().substring(2));
            }
        });
        datos.fotos().forEach(foto -> {
            var m = FOTO.matcher(foto.id().toString());
            assertThat(m.matches()).isTrue();
            assertThat(foto.obraId().toString()).endsWith("00" + m.group(1));
            assertThat(foto.partida()).isEqualTo("P-" + m.group(2));
        });
        assertThat(datos.materiales().values()).allMatch(id -> MATERIAL.matcher(id).matches());
        assertThat(datos.fotosRegistradasPor()).isEqualTo(UUID.fromString("a0000000-0000-4000-8000-000000000005"));
    }

    @Test
    void lasRecetasSoloUsanMaterialesConId() {
        datos.recetas().values().forEach(receta -> assertThat(datos.materiales()).containsKeys(
                receta.keySet().toArray(String[]::new)));
        datos.obras().forEach(obra -> obra.partidas().stream().filter(partida -> partida.receta() != null)
                .forEach(partida -> assertThat(datos.recetas()).containsKey(partida.receta())));
    }

    private static BigDecimal presupuesto(ObraSemilla obra) {
        return obra.partidas().stream()
                .map(partida -> partida.cantidadContratada().multiply(partida.precioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
