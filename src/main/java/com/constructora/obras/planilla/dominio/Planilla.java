package com.constructora.obras.planilla.dominio;

import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException.ErrorCampo;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Agregado Planilla de avance con sus líneas (una por partida de la obra) y las fotos de avance adjuntas como
 * respaldo. Reglas: docs/reglas-negocio.md («Planilla de avance» y «Estados de la planilla»); 007, criterios 1 a 9.
 * La obra se referencia por id; los métodos que necesitan sus partidas o su contrato la reciben.
 */
public final class Planilla {

    public static final String OBRA_NO_EN_EJECUCION = "OBRA_NO_EN_EJECUCION";
    public static final String PLANILLA_ABIERTA_EXISTE = "PLANILLA_ABIERTA_EXISTE";
    public static final String PERIODO_NO_POSTERIOR = "PERIODO_NO_POSTERIOR";
    public static final String PERIODO_FUTURO = "PERIODO_FUTURO";
    public static final String SUPERA_CANTIDAD_CONTRATADA = "SUPERA_CANTIDAD_CONTRATADA";
    public static final String VERSION_DESACTUALIZADA = "VERSION_DESACTUALIZADA";
    public static final String PLANILLA_NO_EDITABLE = "PLANILLA_NO_EDITABLE";
    public static final String SUBTOTAL_CERO = "SUBTOTAL_CERO";
    public static final String FOTO_FUERA_DEL_PERIODO = "FOTO_FUERA_DEL_PERIODO";
    public static final String DATOS_INVALIDOS = "DATOS_INVALIDOS";
    public static final String PLANILLA_NO_PRESENTADA = "PLANILLA_NO_PRESENTADA";
    public static final String MOTIVO_INVALIDO = "MOTIVO_INVALIDO";
    /** Largo del motivo con que el cliente observa (008, criterio 4), contado sin los espacios de los extremos. */
    public static final int MOTIVO_MINIMO = 10;
    public static final int MOTIVO_MAXIMO = 500;

    private final UUID id;
    private final UUID obraId;
    private final int numero;
    private final Periodo periodo;
    private final int version;
    private String observacion;
    private UUID cobroId;
    private String cobroNumero;
    private EstadoPlanilla estado;
    /** partidaId → línea, en el orden en que se armaron. */
    private final Map<UUID, LineaPlanilla> lineas;
    private TotalesPlanilla totales;
    private Set<UUID> fotoIds;

    private Planilla(UUID id, UUID obraId, int numero, Periodo periodo, EstadoPlanilla estado,
            List<LineaPlanilla> lineas, TotalesPlanilla totales, String observacion, UUID cobroId,
            String cobroNumero, int version, Collection<UUID> fotoIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.obraId = Objects.requireNonNull(obraId, "obraId");
        this.numero = numero;
        this.periodo = Objects.requireNonNull(periodo, "periodo");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.totales = Objects.requireNonNull(totales, "totales");
        this.observacion = observacion;
        this.cobroId = cobroId;
        this.cobroNumero = cobroNumero;
        this.version = version;
        this.lineas = new LinkedHashMap<>();
        lineas.forEach(linea -> this.lineas.put(linea.partidaId(), linea));
        this.fotoIds = new TreeSet<>(fotoIds);
    }

    /**
     * Crea la planilla del periodo en BORRADOR, con una línea por partida de la obra en cantidad 0 y totales en 0
     * (criterio 1). Valida, en este orden: obra EN_EJECUCION (OBRA_NO_EN_EJECUCION), que no haya otra planilla sin
     * aprobar (PLANILLA_ABIERTA_EXISTE), que el periodo sea posterior al de la última planilla (PERIODO_NO_POSTERIOR)
     * y que no sea un mes futuro (PERIODO_FUTURO) (criterio 2).
     *
     * @param numero        número siguiente de la obra (correlativo desde 1)
     * @param hayAbierta    la obra ya tiene una planilla que no está APROBADA (lo consulta el caso de uso)
     * @param periodoUltima periodo de la última planilla de la obra, si tiene
     * @param hoy           fecha de hoy en la zona del negocio (Reloj.hoy())
     */
    public static Planilla crear(Obra obra, Periodo periodo, int numero, boolean hayAbierta,
            Optional<Periodo> periodoUltima, LocalDate hoy) {
        Objects.requireNonNull(obra, "obra");
        Objects.requireNonNull(periodo, "periodo");
        if (obra.estado() != EstadoObra.EN_EJECUCION) {
            throw new ReglaNegocioException(OBRA_NO_EN_EJECUCION, "La obra no está en ejecución");
        }
        if (hayAbierta) {
            throw new ReglaNegocioException(PLANILLA_ABIERTA_EXISTE,
                    "Ya hay una planilla sin aprobar en esta obra. Termínala antes de crear otra.");
        }
        if (periodoUltima.isPresent() && !periodo.mes().isAfter(periodoUltima.get().mes())) {
            throw new ReglaNegocioException(PERIODO_NO_POSTERIOR,
                    "El periodo debe ser posterior al de la última planilla (" + periodoUltima.get() + ").",
                    List.of(new ErrorCampo("periodo", "Debe ser posterior a " + periodoUltima.get())));
        }
        if (periodo.mes().isAfter(Periodo.de(hoy).mes())) {
            throw new ReglaNegocioException(PERIODO_FUTURO,
                    "No se puede crear la planilla de un mes que todavía no llegó.");
        }
        List<LineaPlanilla> lineas = obra.partidas().stream()
                .sorted(Comparator.comparing(Partida::codigo))
                .map(partida -> LineaPlanilla.de(partida.id(), BigDecimal.ZERO, partida.precioUnitario()))
                .toList();
        return new Planilla(UUID.randomUUID(), obra.id(), numero, periodo, EstadoPlanilla.BORRADOR, lineas,
                TotalesPlanilla.CERO, null, null, null, 0, List.of());
    }

    /** Vuelve a armar una planilla guardada (persistencia). No valida reglas. */
    public static Planilla reconstruir(UUID id, UUID obraId, int numero, Periodo periodo, EstadoPlanilla estado,
            List<LineaPlanilla> lineas, TotalesPlanilla totales, String observacion, UUID cobroId,
            String cobroNumero, int version, Collection<UUID> fotoIds) {
        return new Planilla(id, obraId, numero, periodo, estado, lineas, totales, observacion, cobroId, cobroNumero,
                version, fotoIds);
    }

    /** Solo se editan (y se presentan) planillas en BORRADOR u OBSERVADA (criterio 9). */
    public boolean esEditable() {
        return estado == EstadoPlanilla.BORRADOR || estado == EstadoPlanilla.OBSERVADA;
    }

    /**
     * Antes de guardar o presentar: la planilla debe ser editable (PLANILLA_NO_EDITABLE) y la versión que manda
     * quien la editó debe ser la guardada (VERSION_DESACTUALIZADA, criterio 7).
     */
    public void verificarEdicion(int versionEditada) {
        if (!esEditable()) {
            throw new ReglaNegocioException(PLANILLA_NO_EDITABLE,
                    "La planilla ya no se puede editar (estado " + estado + ")");
        }
        if (versionEditada != version) {
            throw new ReglaNegocioException(VERSION_DESACTUALIZADA,
                    "La planilla cambió, recarga para ver la última versión");
        }
    }

    /**
     * Cambia la cantidad del periodo de las partidas indicadas (las demás quedan como estaban) y recalcula montos y
     * totales con el contrato de la obra. Regla: acumulado anterior + cantidad del periodo ≤ cantidad contratada
     * (criterio 5); si alguna línea la supera, no cambia nada y lanza SUPERA_CANTIDAD_CONTRATADA con un error por
     * partida (campo = id de la partida, mensaje con el saldo).
     *
     * @param cantidades           cantidad del periodo por partida, en el orden del pedido
     * @param acumuladosAnteriores ejecutada acumulada de cada partida en las planillas aprobadas anteriores; las que
     *                             no figuran cuentan 0
     * @param obra                 la obra de la planilla (partidas, precios y porcentajes del contrato)
     */
    public void actualizarLineas(List<CantidadLinea> cantidades, Map<UUID, BigDecimal> acumuladosAnteriores,
            Obra obra) {
        verificarObra(obra);
        if (!esEditable()) {
            throw new ReglaNegocioException(PLANILLA_NO_EDITABLE,
                    "La planilla ya no se puede editar (estado " + estado + ")");
        }
        Map<UUID, BigDecimal> nuevas = new LinkedHashMap<>();
        lineas.values().forEach(linea -> nuevas.put(linea.partidaId(), linea.cantidadPeriodo()));
        Set<UUID> pedidas = new HashSet<>();
        for (int i = 0; i < cantidades.size(); i++) {
            CantidadLinea pedida = cantidades.get(i);
            if (!lineas.containsKey(pedida.partidaId()) || !pedidas.add(pedida.partidaId())) {
                throw new ReglaNegocioException(DATOS_INVALIDOS, "Algunos datos no son válidos.",
                        List.of(new ErrorCampo("lineas[" + i + "].partidaId",
                                "Debe ser una partida de la planilla, sin repetir")));
            }
            if (pedida.cantidadPeriodo().signum() < 0) {
                throw new ReglaNegocioException(DATOS_INVALIDOS, "Algunos datos no son válidos.",
                        List.of(new ErrorCampo("lineas[" + i + "].cantidadPeriodo", "Debe ser mayor o igual a 0")));
            }
            nuevas.put(pedida.partidaId(), pedida.cantidadPeriodo());
        }
        Map<UUID, Partida> partidas = partidasPorId(obra);
        List<ErrorCampo> errores = new ArrayList<>();
        List<LineaPlanilla> calculadas = new ArrayList<>();
        nuevas.forEach((partidaId, cantidad) -> {
            Partida partida = partidas.get(partidaId);
            BigDecimal anterior = acumuladosAnteriores.getOrDefault(partidaId, BigDecimal.ZERO);
            if (anterior.add(cantidad).compareTo(partida.cantidadContratada()) > 0) {
                errores.add(new ErrorCampo(partidaId.toString(), "Supera lo contratado (saldo: "
                        + partida.cantidadContratada().subtract(anterior).stripTrailingZeros().toPlainString() + ")"));
            }
            calculadas.add(LineaPlanilla.de(partidaId, cantidad, partida.precioUnitario()));
        });
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(SUPERA_CANTIDAD_CONTRATADA,
                    "Hay filas que superan lo contratado. Corrígelas para guardar.", errores);
        }
        lineas.clear();
        calculadas.forEach(linea -> lineas.put(linea.partidaId(), linea));
        totales = TotalesPlanilla.calcular(lineas.values().stream().map(LineaPlanilla::monto).toList(),
                obra.contrato().anticipoPct(), obra.contrato().retencionPct());
    }

    /**
     * Reemplaza las fotos adjuntas (criterio 6): cada una debe ser una foto de avance de esta obra registrada dentro
     * del periodo (mes de registradoEn en la zona del negocio). Si alguna no lo es (o no existe), no cambia nada y
     * lanza FOTO_FUERA_DEL_PERIODO con un error por foto (campo = id de la foto).
     *
     * @param pedidas     ids de las fotos a adjuntar (los repetidos cuentan una vez)
     * @param encontradas fotos que existen, por id
     */
    public void adjuntarFotos(Collection<UUID> pedidas, Map<UUID, FotoAvance> encontradas) {
        if (!esEditable()) {
            throw new ReglaNegocioException(PLANILLA_NO_EDITABLE,
                    "La planilla ya no se puede editar (estado " + estado + ")");
        }
        Set<UUID> unicas = new LinkedHashSet<>(pedidas);
        List<ErrorCampo> errores = new ArrayList<>();
        for (UUID fotoId : unicas) {
            FotoAvance foto = encontradas.get(fotoId);
            if (foto == null || !foto.obraId().equals(obraId) || !esDelPeriodo(foto)) {
                errores.add(new ErrorCampo(fotoId.toString(), "No es una foto de la obra registrada en " + periodo));
            }
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(FOTO_FUERA_DEL_PERIODO,
                    "Una de las fotos adjuntas no es de este periodo. Quítala para guardar.", errores);
        }
        fotoIds = new TreeSet<>(unicas);
    }

    /**
     * Presenta la planilla al cliente (criterio 8): debe ser editable (BORRADOR u OBSERVADA), con la versión guardada
     * y subtotal mayor a 0 (SUBTOTAL_CERO). Queda PRESENTADA; las fotos adjuntas se publican (lo hace el caso de uso).
     */
    public void presentar(int versionEditada) {
        verificarEdicion(versionEditada);
        if (!totales.subtotal().esPositivo()) {
            throw new ReglaNegocioException(SUBTOTAL_CERO,
                    "No se puede presentar una planilla con subtotal 0. Indica las cantidades ejecutadas del periodo.");
        }
        estado = EstadoPlanilla.PRESENTADA;
    }

    /**
     * Líneas como las ve el usuario (criterio 3), por código de partida: datos de la partida, acumulado anterior,
     * cantidad del periodo, acumulado total, saldo por ejecutar (contratada − acumulado total) y monto.
     *
     * @param acumuladosAnteriores ejecutada acumulada de cada partida en las planillas aprobadas anteriores
     */
    public List<LineaDetalle> detalle(Obra obra, Map<UUID, BigDecimal> acumuladosAnteriores) {
        verificarObra(obra);
        Map<UUID, Partida> partidas = partidasPorId(obra);
        return lineas.values().stream()
                .filter(linea -> partidas.containsKey(linea.partidaId()))
                .map(linea -> {
                    Partida partida = partidas.get(linea.partidaId());
                    BigDecimal anterior = acumuladosAnteriores.getOrDefault(linea.partidaId(), BigDecimal.ZERO)
                            .setScale(3, RoundingMode.HALF_UP);
                    BigDecimal total = anterior.add(linea.cantidadPeriodo());
                    return new LineaDetalle(partida, anterior, linea.cantidadPeriodo(), total,
                            partida.cantidadContratada().subtract(total), linea.monto());
                })
                .sorted(Comparator.comparing(detalle -> detalle.partida().codigo()))
                .toList();
    }

    /**
     * El cliente aprueba la planilla en el portal (008, criterio 5): solo si está PRESENTADA (si no,
     * PLANILLA_NO_PRESENTADA). Queda APROBANDO mientras corre la saga.
     */
    public void aprobarPorCliente() {
        exigirPresentada();
        estado = EstadoPlanilla.APROBANDO;
    }

    /**
     * El cliente observa la planilla con un motivo obligatorio de 10 a 500 caracteres (008, criterio 4): solo si está
     * PRESENTADA (PLANILLA_NO_PRESENTADA, se comprueba primero) y con el motivo válido (MOTIVO_INVALIDO). Queda
     * OBSERVADA con el motivo (sin los espacios de los extremos) en la observación.
     */
    public void observarPorCliente(String motivo) {
        exigirPresentada();
        String texto = motivo == null ? "" : motivo.strip();
        int largo = texto.codePointCount(0, texto.length());
        if (largo < MOTIVO_MINIMO || largo > MOTIVO_MAXIMO) {
            throw new ReglaNegocioException(MOTIVO_INVALIDO, "El motivo debe tener entre 10 y 500 caracteres.",
                    List.of(new ErrorCampo("motivo", "Debe tener entre 10 y 500 caracteres")));
        }
        estado = EstadoPlanilla.OBSERVADA;
        observacion = texto;
    }

    /**
     * Paso 5 de la saga (008, criterio 7): todos los pasos terminaron bien. Queda APROBADA con el cobro que emitió
     * Finanzas; desde ahora cuenta para la ejecutada acumulada y el avance de la obra (criterio 13).
     */
    public void marcarAprobada(UUID cobroId, String cobroNumero) {
        exigirAprobando();
        this.estado = EstadoPlanilla.APROBADA;
        this.cobroId = Objects.requireNonNull(cobroId, "cobroId");
        this.cobroNumero = Objects.requireNonNull(cobroNumero, "cobroNumero");
    }

    /** Falló un paso de la saga (008, criterios 8 a 11): queda OBSERVADA con el motivo del fallo. */
    public void observarPorSaga(String motivo) {
        exigirAprobando();
        estado = EstadoPlanilla.OBSERVADA;
        observacion = Objects.requireNonNull(motivo, "motivo");
    }

    private void exigirPresentada() {
        if (estado != EstadoPlanilla.PRESENTADA) {
            throw new ReglaNegocioException(PLANILLA_NO_PRESENTADA,
                    "Esta planilla ya no está esperando tu revisión. Recarga para ver su estado actual.");
        }
    }

    private void exigirAprobando() {
        if (estado != EstadoPlanilla.APROBANDO) {
            throw new IllegalStateException("La planilla " + id + " no está APROBANDO (estado " + estado + ")");
        }
    }

    /** Planilla sin aprobar: cuenta para «solo una planilla abierta por obra». */
    public boolean estaAbierta() {
        return estado != EstadoPlanilla.APROBADA;
    }

    private boolean esDelPeriodo(FotoAvance foto) {
        return Periodo.de(foto.registradoEn().atZone(Reloj.ZONA_NEGOCIO).toLocalDate()).equals(periodo);
    }

    private void verificarObra(Obra obra) {
        if (!obra.id().equals(obraId)) {
            throw new IllegalArgumentException("La obra no es la de la planilla");
        }
    }

    private static Map<UUID, Partida> partidasPorId(Obra obra) {
        Map<UUID, Partida> partidas = new LinkedHashMap<>();
        obra.partidas().forEach(partida -> partidas.put(partida.id(), partida));
        return partidas;
    }

    public UUID id() {
        return id;
    }

    public UUID obraId() {
        return obraId;
    }

    public int numero() {
        return numero;
    }

    public Periodo periodo() {
        return periodo;
    }

    public EstadoPlanilla estado() {
        return estado;
    }

    public List<LineaPlanilla> lineas() {
        return List.copyOf(lineas.values());
    }

    public TotalesPlanilla totales() {
        return totales;
    }

    public String observacion() {
        return observacion;
    }

    public UUID cobroId() {
        return cobroId;
    }

    public String cobroNumero() {
        return cobroNumero;
    }

    /** Versión leída de la base (bloqueo optimista). */
    public int version() {
        return version;
    }

    /** Fotos adjuntas, ordenadas por id. */
    public List<UUID> fotoIds() {
        return List.copyOf(fotoIds);
    }

    /** Cantidad del periodo que pide quien guarda la planilla para una partida. */
    public record CantidadLinea(UUID partidaId, BigDecimal cantidadPeriodo) {

        public CantidadLinea {
            Objects.requireNonNull(partidaId, "partidaId");
            Objects.requireNonNull(cantidadPeriodo, "cantidadPeriodo");
        }
    }

    /** Línea de la planilla con los datos de su partida y los acumulados (criterio 3). */
    public record LineaDetalle(Partida partida, BigDecimal acumuladoAnterior, BigDecimal cantidadPeriodo,
            BigDecimal acumuladoTotal, BigDecimal saldo, Dinero monto) {
    }
}
