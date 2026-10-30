package com.constructora.obras.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.constructora.obras.planilla.dominio.LineaPlanilla;
import com.constructora.obras.semilla.PlanSemilla.FilaPlanilla;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 011 · Filas resueltas con D = 2026-10-28: montos y totales calculados con el dominio, comparados con las cifras de
 * docs/datos-prueba.md (sección 7), y número del cobro por año de emisión (sección 8).
 */
class PlanSemillaTest {

    private static final LocalDate D = LocalDate.parse("2026-10-28");

    private final PlanSemilla plan = PlanSemilla.armar(DatosSemilla.leer(), new FechaRelativa(D));

    @Test
    void cantidadesDeFilas() {
        assertThat(plan.obras()).hasSize(3);
        assertThat(plan.partidas()).hasSize(21);
        assertThat(plan.insumos()).hasSize(58);
        assertThat(plan.planillas()).hasSize(5);
        assertThat(plan.planillas().stream().mapToInt(planilla -> planilla.lineas().size()).sum()).isEqualTo(34);
        assertThat(plan.fotos()).hasSize(10);
    }

    @Test
    void totalesDeLasCincoPlanillas() {
        totales("d0000000-0000-4000-8000-000000000101", "349560.00", "34956.00", "24469.20", "290134.80");
        totales("d0000000-0000-4000-8000-000000000102", "440680.00", "44068.00", "30847.60", "365764.40");
        totales("d0000000-0000-4000-8000-000000000103", "397770.00", "39777.00", "27843.90", "330149.10");
        totales("d0000000-0000-4000-8000-000000000201", "520340.00", "78051.00", "36423.80", "405865.20");
        totales("d0000000-0000-4000-8000-000000000202", "131600.00", "19740.00", "9212.00", "102648.00");
    }

    @Test
    void lineasDeLaPlanilla3DeLaObra1() {
        FilaPlanilla planilla = planilla("d0000000-0000-4000-8000-000000000103");
        assertThat(planilla.estado()).isEqualTo("PRESENTADA");
        assertThat(planilla.periodo()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(planilla.version()).isEqualTo(1);
        assertThat(planilla.cobroId()).isNull();
        assertThat(planilla.cobroNumero()).isNull();
        Map<UUID, BigDecimal> montos = new LinkedHashMap<>();
        planilla.lineas().forEach(linea -> montos.put(linea.partidaId(), linea.monto().valor()));
        assertThat(montos).hasSize(6);
        assertThat(montos.get(partida(1, 1))).isEqualByComparingTo("0");
        assertThat(montos.get(partida(1, 3))).isEqualByComparingTo("134600.00");
        assertThat(montos.get(partida(1, 4))).isEqualByComparingTo("209250.00");
        assertThat(montos.get(partida(1, 5))).isEqualByComparingTo("53920.00");
        assertThat(planilla.fotoIds()).containsExactly(
                UUID.fromString("f1000000-0000-4000-8000-000000010302"),
                UUID.fromString("f1000000-0000-4000-8000-000000010402"),
                UUID.fromString("f1000000-0000-4000-8000-000000010502"));
    }

    @Test
    void planillaObservadaConElMotivoDelCliente() {
        FilaPlanilla planilla = planilla("d0000000-0000-4000-8000-000000000202");
        assertThat(planilla.estado()).isEqualTo("OBSERVADA");
        assertThat(planilla.observacion()).startsWith("La medición de columnas y vigas (40 m³)");
    }

    @Test
    void numeroDelCobroPorOrdenDeEmisionDentroDelAnio() {
        assertThat(planilla("d0000000-0000-4000-8000-000000000101").cobroNumero()).isEqualTo("COB-2026-0001");
        assertThat(planilla("d0000000-0000-4000-8000-000000000201").cobroNumero()).isEqualTo("COB-2026-0002");
        assertThat(planilla("d0000000-0000-4000-8000-000000000102").cobroNumero()).isEqualTo("COB-2026-0003");
        assertThat(planilla("d0000000-0000-4000-8000-000000000101").cobroId())
                .isEqualTo(UUID.fromString("c2000000-0000-4000-8000-000000000101"));
    }

    @Test
    void elCorrelativoDelCobroEmpiezaDeNuevoEnElAnioNuevo() {
        PlanSemilla enero = PlanSemilla.armar(DatosSemilla.leer(), new FechaRelativa(LocalDate.parse("2027-01-20")));
        assertThat(cobro(enero, "d0000000-0000-4000-8000-000000000101")).isEqualTo("COB-2026-0001");
        assertThat(cobro(enero, "d0000000-0000-4000-8000-000000000201")).isEqualTo("COB-2026-0002");
        assertThat(cobro(enero, "d0000000-0000-4000-8000-000000000102")).isEqualTo("COB-2027-0001");
    }

    @Test
    void fechasMesesYFotosResueltos() {
        assertThat(plan.obras().get(0).fechaInicio()).isEqualTo(LocalDate.parse("2026-06-30"));
        assertThat(plan.obras().get(1).fechaInicio()).isEqualTo(LocalDate.parse("2026-05-31"));
        assertThat(plan.obras().get(2).fechaInicio()).isEqualTo(LocalDate.parse("2026-10-23"));
        assertThat(plan.partidas().get(1).meses()).isEqualTo("jul-ago");
        assertThat(plan.partidas().get(0).meses()).isEqualTo("jul");
        var foto = plan.fotos().get(1);
        assertThat(foto.id()).isEqualTo(UUID.fromString("f1000000-0000-4000-8000-000000010302"));
        assertThat(foto.claveArchivo())
                .isEqualTo("fotos/b0000000-0000-4000-8000-000000000001/f1000000-0000-4000-8000-000000010302.jpg");
        assertThat(foto.registradoEn()).isEqualTo(Instant.parse("2026-09-15T15:00:00Z"));
        assertThat(foto.publicadaCliente()).isTrue();
        assertThat(plan.fotos().get(9).registradoEn()).isEqualTo(Instant.parse("2026-10-27T15:00:00Z"));
    }

    @Test
    void unaCantidadQueSuperaLoContratadoDetieneLaCarga() {
        DatosSemilla datos = DatosSemilla.leer();
        List<DatosSemilla.PlanillaSemilla> planillas = new ArrayList<>(datos.planillas());
        DatosSemilla.PlanillaSemilla tercera = planillas.get(2);
        planillas.set(2, new DatosSemilla.PlanillaSemilla(tercera.id(), tercera.obraId(), tercera.numero(),
                tercera.periodo(), tercera.estado(), Map.of("P-03", new BigDecimal("91")), null, null, List.of()));
        DatosSemilla alterados = new DatosSemilla(datos.fuente(), datos.materiales(), datos.recetas(), datos.obras(),
                datos.fotos(), datos.fotosRegistradasPor(), planillas);

        assertThatThrownBy(() -> PlanSemilla.armar(alterados, new FechaRelativa(D)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("por encima de lo contratado");
    }

    private void totales(String planillaId, String subtotal, String anticipo, String retencion, String neto) {
        FilaPlanilla planilla = planilla(planillaId);
        assertThat(planilla.totales().subtotal().valor()).isEqualByComparingTo(subtotal);
        assertThat(planilla.totales().descuentoAnticipo().valor()).isEqualByComparingTo(anticipo);
        assertThat(planilla.totales().retencion().valor()).isEqualByComparingTo(retencion);
        assertThat(planilla.totales().neto().valor()).isEqualByComparingTo(neto);
        BigDecimal suma = planilla.lineas().stream().map(LineaPlanilla::monto).map(m -> m.valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(subtotal);
    }

    private FilaPlanilla planilla(String id) {
        return plan.planillas().stream().filter(p -> p.id().equals(UUID.fromString(id))).findFirst().orElseThrow();
    }

    private static String cobro(PlanSemilla plan, String id) {
        return plan.planillas().stream().filter(p -> p.id().equals(UUID.fromString(id))).findFirst().orElseThrow()
                .cobroNumero();
    }

    private static UUID partida(int obra, int numero) {
        return UUID.fromString("b1000000-0000-4000-8000-000000000%d%02d".formatted(obra, numero));
    }
}
