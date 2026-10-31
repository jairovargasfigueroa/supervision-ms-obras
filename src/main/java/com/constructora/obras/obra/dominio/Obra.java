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
import java.util.Comparator;
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
    private final Zona zona;
    private final LocalDate fechaInicio;
    private final Asignaciones asignaciones;
    private final List<Partida> partidas;
    private EstadoObra estado;
    private ContratoCopia contrato;
    private String motivoRechazoContrato;

    private Obra(UUID id, String nombre, String ubicacion, Zona zona, LocalDate fechaInicio, EstadoObra estado,
            Asignaciones asignaciones, ContratoCopia contrato, String motivoRechazoContrato, List<Partida> partidas) {
        this.id = Objects.requireNonNull(id, "id");
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.ubicacion = Objects.requireNonNull(ubicacion, "ubicacion");
        this.zona = zona;
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
     *
     * @param zona opcional (401): null si no se indicó
     */
    public static Obra registrar(String nombre, String ubicacion, Zona zona, LocalDate fechaInicio,
            Asignaciones asignaciones, ContratoCopia contrato, List<Partida> partidas) {
        Objects.requireNonNull(contrato, "contrato");
        if (partidas == null || partidas.isEmpty()) {
            throw new IllegalArgumentException("La obra necesita al menos una partida");
        }
        contrato.validar();
        validarCodigosUnicos(partidas);
        validarInsumosUnicos(partidas);
        validarPresupuesto(partidas, contrato.monto());
        return new Obra(UUID.randomUUID(), nombre, ubicacion, zona, fechaInicio, EstadoObra.SIN_CONTRATO, asignaciones,
                contrato.conContratoId(null), null, partidas);
    }

    /** Vuelve a armar una obra ya guardada (persistencia). No valida reglas de registro. */
    public static Obra reconstruir(UUID id, String nombre, String ubicacion, Zona zona, LocalDate fechaInicio,
            EstadoObra estado, Asignaciones asignaciones, ContratoCopia contrato, String motivoRechazoContrato,
            List<Partida> partidas) {
        return new Obra(id, nombre, ubicacion, zona, fechaInicio, estado, asignaciones, contrato,
                motivoRechazoContrato, partidas);
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
     * Fotos de avance (005, criterio 7): las registran el maestro y el residente asignados a la obra. Los roles ya
     * son los efectivos (regla del azp).
     */
    public boolean puedeRegistrarAvance(Usuario usuario) {
        String id = usuario.id();
        return usuario.tiene("residente") && id.equals(asignaciones.residenteId().toString())
                || usuario.tiene("maestro") && id.equals(asignaciones.maestroId().toString());
    }

    /**
     * Diario de obra (103, criterio 10): registra entradas solo el maestro asignado a la obra. Los roles ya son los
     * efectivos (regla del azp).
     */
    public boolean puedeRegistrarDiario(Usuario usuario) {
        return usuario.tiene("maestro") && usuario.id().equals(asignaciones.maestroId().toString());
    }

    /**
     * Diario de obra (103, criterio 10): lo consultan admin y gerente, y el maestro y el residente asignados; el
     * cliente no. Los roles ya son los efectivos (regla del azp).
     */
    public boolean puedeVerDiario(Usuario usuario) {
        String id = usuario.id();
        return usuario.tiene("admin") || usuario.tiene("gerente")
                || usuario.tiene("maestro") && id.equals(asignaciones.maestroId().toString())
                || usuario.tiene("residente") && id.equals(asignaciones.residenteId().toString());
    }

    /**
     * Planillas (007, criterio 10): las elabora, guarda y presenta solo el residente asignado a la obra. Los roles ya
     * son los efectivos (regla del azp).
     */
    public boolean puedeElaborarPlanilla(Usuario usuario) {
        return usuario.tiene("residente") && usuario.id().equals(asignaciones.residenteId().toString());
    }

    /**
     * Aprobar u observar una planilla (008): solo el cliente de la obra. Los roles ya son los efectivos (regla del
     * azp: desde el portal solo cuenta cliente).
     */
    public boolean puedeRevisarPlanilla(Usuario usuario) {
        return usuario.tiene("cliente") && usuario.id().equals(asignaciones.clienteId().toString());
    }

    /**
     * El usuario ve la obra solo por ser su cliente (por ejemplo desde el portal): no ve las planillas en BORRADOR
     * (007) ni las fotos sin publicar (005). Si además la ve con un rol interno, no cuenta como solo cliente.
     */
    public boolean veSoloComoCliente(Usuario usuario) {
        if (!esVisiblePara(usuario)) {
            return false;
        }
        Set<String> internos = new HashSet<>(usuario.roles());
        internos.remove("cliente");
        return !esVisiblePara(new Usuario(usuario.id(), internos));
    }

    /**
     * Material previsto (006): lo consultan admin y gerente, y el residente asignado a la obra. Los roles ya son los
     * efectivos (regla del azp).
     */
    public boolean puedeVerMaterialPrevisto(Usuario usuario) {
        return usuario.tiene("admin") || usuario.tiene("gerente")
                || usuario.tiene("residente") && usuario.id().equals(asignaciones.residenteId().toString());
    }

    /**
     * Material previsto del periodo (docs/reglas-negocio.md, «Presupuesto por partidas»; 006, criterio 5): por cada
     * partida y cada material previsto por unidad, cantidad medida en el periodo × cantidad por unidad, redondeado a
     * 3 decimales. Ordenado por código de partida y material.
     *
     * @param cantidadPeriodoPorPartida cantidad de cada partida en la planilla del periodo; las que no figuran
     *                                  cuentan 0
     */
    public List<MaterialPrevisto> materialPrevisto(Map<UUID, BigDecimal> cantidadPeriodoPorPartida) {
        List<MaterialPrevisto> previstos = new ArrayList<>();
        partidas.stream().sorted(Comparator.comparing(Partida::codigo)).forEach(partida -> {
            BigDecimal cantidad = cantidadPeriodoPorPartida.getOrDefault(partida.id(), BigDecimal.ZERO);
            partida.insumos().stream().sorted(Comparator.comparing(InsumoPrevisto::materialId))
                    .forEach(insumo -> previstos.add(new MaterialPrevisto(partida.id(), insumo.materialId(),
                            cantidad.multiply(insumo.cantidadPorUnidad()).setScale(3, RoundingMode.HALF_UP))));
        });
        return List.copyOf(previstos);
    }

    /** Ids de las partidas de la obra. */
    public Set<UUID> idsPartidas() {
        Set<UUID> ids = new HashSet<>();
        partidas.forEach(partida -> ids.add(partida.id()));
        return Set.copyOf(ids);
    }

    /**
     * Cantidad ejecutada acumulada de cada partida de la obra, con 3 decimales (docs/reglas-negocio.md, «Avance y
     * plazo»: suma de cantidad_periodo de sus líneas en planillas APROBADA).
     *
     * @param ejecutadaPorPartida suma de las planillas aprobadas por partida; las partidas que no figuran cuentan 0
     *                            y las que no son de esta obra se ignoran
     */
    public Map<UUID, BigDecimal> ejecutadaAcumulada(Map<UUID, BigDecimal> ejecutadaPorPartida) {
        Map<UUID, BigDecimal> ejecutadas = new HashMap<>();
        for (Partida partida : partidas) {
            ejecutadas.put(partida.id(), ejecutadaPorPartida.getOrDefault(partida.id(), BigDecimal.ZERO)
                    .setScale(3, RoundingMode.HALF_UP));
        }
        return Map.copyOf(ejecutadas);
    }

    /**
     * Avance de la obra (%) = Σ (ejecutada acumulada × precio unitario) / monto del contrato × 100, con 2 decimales.
     *
     * @param ejecutadaPorPartida cantidad ejecutada acumulada de cada partida (planillas APROBADA)
     */
    public BigDecimal avance(Map<UUID, BigDecimal> ejecutadaPorPartida) {
        Map<UUID, BigDecimal> ejecutadas = ejecutadaAcumulada(ejecutadaPorPartida);
        BigDecimal ejecutadoBs = partidas.stream()
                .map(partida -> ejecutadas.get(partida.id()).multiply(partida.precioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return porcentaje(ejecutadoBs, contrato.monto().valor());
    }

    /**
     * Plazo transcurrido (%) = días desde la fecha de inicio hasta hoy / plazo en días × 100, entre 0 y 100, con 2
     * decimales. Antes de iniciar es 0; desde el último día del plazo en adelante, 100.
     *
     * @param hoy fecha de hoy en la zona del negocio (Reloj.hoy())
     */
    public BigDecimal plazoTranscurrido(LocalDate hoy) {
        BigDecimal dias = BigDecimal.valueOf(ChronoUnit.DAYS.between(fechaInicio, hoy));
        return porcentaje(dias, BigDecimal.valueOf(contrato.plazoDias()))
                .max(BigDecimal.ZERO.setScale(2)).min(CIEN.setScale(2));
    }

    /** Obra en riesgo (decisión del equipo): plazo transcurrido − avance > 10 puntos. */
    public static boolean enRiesgo(BigDecimal avancePct, BigDecimal plazoPct) {
        return plazoPct.subtract(avancePct).compareTo(UMBRAL_RIESGO) > 0;
    }

    /**
     * Avance, plazo transcurrido y riesgo de la obra y, por partida, ejecutada acumulada y avance (%)
     * (docs/reglas-negocio.md, «Avance y plazo»).
     *
     * @param ejecutadaPorPartida cantidad ejecutada acumulada de cada partida (planillas APROBADA); las que no
     *                            figuran cuentan 0
     * @param hoy                 fecha de hoy en la zona del negocio (Reloj.hoy())
     */
    public Indicadores indicadores(Map<UUID, BigDecimal> ejecutadaPorPartida, LocalDate hoy) {
        Map<UUID, BigDecimal> ejecutadas = ejecutadaAcumulada(ejecutadaPorPartida);
        Map<UUID, BigDecimal> avancePorPartida = new HashMap<>();
        for (Partida partida : partidas) {
            avancePorPartida.put(partida.id(), porcentaje(ejecutadas.get(partida.id()), partida.cantidadContratada()));
        }
        BigDecimal avance = avance(ejecutadas);
        BigDecimal plazo = plazoTranscurrido(hoy);
        return new Indicadores(avance, plazo, enRiesgo(avance, plazo), ejecutadas, Map.copyOf(avancePorPartida));
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

    /** Zona de la obra (401); null si no se indicó. */
    public Zona zona() {
        return zona;
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
