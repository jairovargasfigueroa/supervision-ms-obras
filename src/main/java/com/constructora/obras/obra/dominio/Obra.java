package com.constructora.obras.obra.dominio;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.compartido.dominio.Usuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado Obra: datos, asignaciones, copia del contrato y presupuesto por partidas con su material previsto.
 * Reglas: docs/reglas-negocio.md («Obra», «Contrato», «Presupuesto por partidas», «Avance y plazo»).
 */
public final class Obra {

    /** Diferencia máxima entre el presupuesto y el monto del contrato (002, criterio 6). */
    static final BigDecimal TOLERANCIA_PRESUPUESTO = new BigDecimal("0.01");
    /** Obra en riesgo: plazo transcurrido − avance > 10 puntos (decisión del equipo). */
    static final BigDecimal UMBRAL_RIESGO = BigDecimal.TEN;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final UUID id;
    private final String nombre;
    private final String ubicacion;
    private final LocalDate fechaInicio;
    private final Asignaciones asignaciones;
    private final List<Partida> partidas;
    private EstadoObra estado;
    private ContratoCopia contrato;
    private String motivoRechazoContrato;

    private Obra(UUID id, String nombre, String ubicacion, LocalDate fechaInicio, EstadoObra estado,
            Asignaciones asignaciones, ContratoCopia contrato, String motivoRechazoContrato, List<Partida> partidas) {
        this.id = Objects.requireNonNull(id, "id");
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.ubicacion = Objects.requireNonNull(ubicacion, "ubicacion");
        this.fechaInicio = Objects.requireNonNull(fechaInicio, "fechaInicio");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.asignaciones = Objects.requireNonNull(asignaciones, "asignaciones");
        this.contrato = Objects.requireNonNull(contrato, "contrato");
        this.motivoRechazoContrato = motivoRechazoContrato;
        this.partidas = List.copyOf(partidas);
    }

    /**
     * Registra una obra nueva en SIN_CONTRATO. Valida, en este orden: el contrato (CONTRATO_INVALIDO), que los
     * códigos de partida no se repitan (CODIGO_PARTIDA_REPETIDO), que ninguna partida repita material
     * (INSUMO_REPETIDO) y que el presupuesto coincida con el monto del contrato (PRESUPUESTO_NO_COINCIDE).
     */
    public static Obra registrar(String nombre, String ubicacion, LocalDate fechaInicio, Asignaciones asignaciones,
            ContratoCopia contrato, List<Partida> partidas) {
        Objects.requireNonNull(contrato, "contrato");
        if (partidas == null || partidas.isEmpty()) {
            throw new IllegalArgumentException("La obra necesita al menos una partida");
        }
        contrato.validar();
        validarCodigosUnicos(partidas);
        validarInsumosUnicos(partidas);
        validarPresupuesto(partidas, contrato.monto());
        return new Obra(UUID.randomUUID(), nombre, ubicacion, fechaInicio, EstadoObra.SIN_CONTRATO, asignaciones,
                contrato.conContratoId(null), null, partidas);
    }

    /** Vuelve a armar una obra ya guardada (persistencia). No valida reglas de registro. */
    public static Obra reconstruir(UUID id, String nombre, String ubicacion, LocalDate fechaInicio, EstadoObra estado,
            Asignaciones asignaciones, ContratoCopia contrato, String motivoRechazoContrato, List<Partida> partidas) {
        return new Obra(id, nombre, ubicacion, fechaInicio, estado, asignaciones, contrato, motivoRechazoContrato,
                partidas);
    }

    /** Σ cantidad × precio unitario, redondeado a 2 decimales al final. */
    public static Dinero presupuesto(List<Partida> partidas) {
        return Dinero.de(partidas.stream().map(Partida::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public Dinero presupuesto() {
        return presupuesto(partidas);
    }

    /**
     * Finanzas registró el contrato: se guarda su id y la obra pasa a EN_EJECUCION. Si la obra ya no está en
     * SIN_CONTRATO (mensaje repetido o tardío) no cambia nada.
     *
     * @return true si la obra cambió
     */
    public boolean registrarContrato(UUID contratoId) {
        Objects.requireNonNull(contratoId, "contratoId");
        if (estado != EstadoObra.SIN_CONTRATO) {
            return false;
        }
        contrato = contrato.conContratoId(contratoId);
        estado = EstadoObra.EN_EJECUCION;
        motivoRechazoContrato = null;
        return true;
    }

    /**
     * Finanzas rechazó el contrato: la obra queda en SIN_CONTRATO con el motivo. Si ya tiene contrato no cambia.
     *
     * @return true si la obra cambió
     */
    public boolean rechazarContrato(String motivo) {
        Objects.requireNonNull(motivo, "motivo");
        if (estado != EstadoObra.SIN_CONTRATO) {
            return false;
        }
        motivoRechazoContrato = motivo;
        return true;
    }

    /**
     * Visibilidad (convenciones, «Autenticación y permisos»): admin y gerente ven todas; residente, maestro y
     * almacenero, las que tienen asignadas; cliente, las suyas. Los roles ya son los efectivos (regla del azp).
     */
    public boolean esVisiblePara(Usuario usuario) {
        if (usuario.tiene("admin") || usuario.tiene("gerente")) {
            return true;
        }
        String id = usuario.id();
        return usuario.tiene("residente") && id.equals(asignaciones.residenteId().toString())
                || usuario.tiene("maestro") && id.equals(asignaciones.maestroId().toString())
                || usuario.tiene("almacenero") && id.equals(asignaciones.almaceneroId().toString())
                || usuario.tiene("cliente") && id.equals(asignaciones.clienteId().toString());
    }

    /**
     * Avance, plazo transcurrido y riesgo (docs/reglas-negocio.md, «Avance y plazo»).
     *
     * @param ejecutadaPorPartida cantidad ejecutada acumulada de cada partida (planillas APROBADA); las que no
     *                            figuran cuentan 0
     * @param hoy                 fecha de hoy en la zona del negocio
     */
    public Indicadores indicadores(Map<UUID, BigDecimal> ejecutadaPorPartida, LocalDate hoy) {
        BigDecimal ejecutadoBs = BigDecimal.ZERO;
        Map<UUID, BigDecimal> avancePorPartida = new HashMap<>();
        Map<UUID, BigDecimal> ejecutadas = new HashMap<>();
        for (Partida partida : partidas) {
            BigDecimal ejecutada = ejecutadaPorPartida.getOrDefault(partida.id(), BigDecimal.ZERO)
                    .setScale(3, RoundingMode.HALF_UP);
            ejecutadas.put(partida.id(), ejecutada);
            ejecutadoBs = ejecutadoBs.add(ejecutada.multiply(partida.precioUnitario()));
            avancePorPartida.put(partida.id(), porcentaje(ejecutada, partida.cantidadContratada()));
        }
        BigDecimal avance = porcentaje(ejecutadoBs, contrato.monto().valor());
        BigDecimal dias = BigDecimal.valueOf(ChronoUnit.DAYS.between(fechaInicio, hoy));
        BigDecimal plazo = porcentaje(dias, BigDecimal.valueOf(contrato.plazoDias()))
                .max(BigDecimal.ZERO.setScale(2)).min(CIEN.setScale(2));
        boolean enRiesgo = plazo.subtract(avance).compareTo(UMBRAL_RIESGO) > 0;
        return new Indicadores(avance, plazo, enRiesgo, Map.copyOf(ejecutadas), Map.copyOf(avancePorPartida));
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return parte.multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static void validarCodigosUnicos(List<Partida> partidas) {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < partidas.size(); i++) {
            String codigo = partidas.get(i).codigo();
            if (!vistos.add(codigo)) {
                throw new ReglaNegocioException("CODIGO_PARTIDA_REPETIDO",
                        "Hay partidas con el mismo código (" + codigo + "). Cada partida debe tener un código distinto.",
                        List.of(new ErrorCampo("partidas[" + i + "].codigo", "Código repetido: " + codigo)));
            }
        }
    }

    private static void validarInsumosUnicos(List<Partida> partidas) {
        for (int i = 0; i < partidas.size(); i++) {
            Partida partida = partidas.get(i);
            Set<String> materiales = new HashSet<>();
            for (int j = 0; j < partida.insumos().size(); j++) {
                String materialId = partida.insumos().get(j).materialId();
                if (!materiales.add(materialId)) {
                    throw new ReglaNegocioException("INSUMO_REPETIDO",
                            "La partida " + partida.codigo() + " tiene el mismo material más de una vez.",
                            List.of(new ErrorCampo("partidas[" + i + "].insumos[" + j + "].materialId",
                                    "Material repetido en la partida " + partida.codigo())));
                }
            }
        }
    }

    private static void validarPresupuesto(List<Partida> partidas, Dinero monto) {
        Dinero presupuesto = presupuesto(partidas);
        if (presupuesto.restar(monto).abs().valor().compareTo(TOLERANCIA_PRESUPUESTO) > 0) {
            List<ErrorCampo> errores = new ArrayList<>();
            errores.add(new ErrorCampo("partidas", "Presupuesto " + presupuesto.formato() + ", contrato "
                    + monto.formato()));
            throw new ReglaNegocioException("PRESUPUESTO_NO_COINCIDE", "El presupuesto (" + presupuesto.formato()
                    + ") no coincide con el monto del contrato (" + monto.formato() + ")", errores);
        }
    }

    public UUID id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public String ubicacion() {
        return ubicacion;
    }

    public LocalDate fechaInicio() {
        return fechaInicio;
    }

    public EstadoObra estado() {
        return estado;
    }

    public Asignaciones asignaciones() {
        return asignaciones;
    }

    public ContratoCopia contrato() {
        return contrato;
    }

    public String motivoRechazoContrato() {
        return motivoRechazoContrato;
    }

    public List<Partida> partidas() {
        return partidas;
    }

    /** Avance de la obra (%), plazo transcurrido (%), riesgo y, por partida, cantidad ejecutada acumulada y avance (%). */
    public record Indicadores(BigDecimal avancePct, BigDecimal plazoPct, boolean enRiesgo,
            Map<UUID, BigDecimal> ejecutadaPorPartida,
            Map<UUID, BigDecimal> avancePorPartida) {
    }
}
