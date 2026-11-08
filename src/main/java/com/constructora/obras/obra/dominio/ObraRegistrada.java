package com.constructora.obras.obra.dominio;

import com.constructora.obras.compartido.dominio.EventoDominio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento evento.obras.ObraRegistrada (docs/contratos.md): lo escuchan Finanzas (crea el contrato), Materiales
 * (guarda las asignaciones) e IA (base de reportes, 401: usa ubicacion y zona). Los componentes son exactamente los
 * "datos" del contrato de eventos; zona es null si la obra no tiene zona.
 */
public record ObraRegistrada(UUID obraId, String nombre, String ubicacion, Zona zona, LocalDate fechaInicio,
        Asignaciones asignaciones, Contrato contrato) implements EventoDominio {

    public static final String TIPO = "ObraRegistrada";

    /** Montos con 2 decimales; firmadoEn no se guarda en Obras: viaja solo en el evento. */
    public record Contrato(BigDecimal monto, int plazoDias, BigDecimal anticipoPct, BigDecimal retencionPct,
            LocalDate firmadoEn) {
    }

    public static ObraRegistrada de(Obra obra, LocalDate firmadoEn) {
        ContratoCopia contrato = obra.contrato();
        return new ObraRegistrada(obra.id(), obra.nombre(), obra.ubicacion(), obra.zona(), obra.fechaInicio(),
                obra.asignaciones(),
                new Contrato(contrato.monto().valor(), contrato.plazoDias(), contrato.anticipoPct(),
                        contrato.retencionPct(), firmadoEn));
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    /** El flujo «registrar obra» se sigue por el id de la obra. */
    @Override
    public UUID correlacionId() {
        return obraId;
    }
}
