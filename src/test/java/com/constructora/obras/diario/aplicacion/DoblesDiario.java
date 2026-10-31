package com.constructora.obras.diario.aplicacion;

import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dobles a mano de los puertos que usan los casos de uso del diario de obra. */
final class DoblesDiario {

    static final LocalDate INICIO = LocalDate.of(2026, 11, 1);

    private DoblesDiario() {
    }

    static Obra obra(Asignaciones asignaciones, EstadoObra estado) {
        Partida partida = Partida.nueva("P-01", "Losa", "m2", BigDecimal.TEN, BigDecimal.valueOf(100), null,
                List.of());
        return Obra.reconstruir(UUID.randomUUID(), "Obra de prueba", "Santa Cruz", null, INICIO, estado, asignaciones,
                ContratoCopia.nuevo(Dinero.de(new BigDecimal("1000")), 100, BigDecimal.TEN, BigDecimal.valueOf(7)),
                null, List.of(partida));
    }

    static Asignaciones asignaciones() {
        return new Asignaciones(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    static final class ObrasEnMemoria implements ObraRepositorio {

        final Map<UUID, Obra> obras = new LinkedHashMap<>();

        ObrasEnMemoria con(Obra obra) {
            obras.put(obra.id(), obra);
            return this;
        }

        @Override
        public void guardar(Obra obra) {
            obras.put(obra.id(), obra);
        }

        @Override
        public Optional<Obra> buscarPorId(UUID id) {
            return Optional.ofNullable(obras.get(id));
        }

        @Override
        public List<Obra> listarTodas(FiltroObras filtro) {
            return List.copyOf(obras.values());
        }

        @Override
        public List<Obra> listarAsignadas(UUID usuarioId, boolean comoResidente, boolean comoMaestro,
                boolean comoAlmacenero, boolean comoCliente, FiltroObras filtro) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Como la tabla: rechaza otro id igual u otra entrada de la misma obra y fecha. {@link #alGuardar} simula otra
     * petición que guarda una entrada justo antes (entre las búsquedas del caso de uso y el INSERT).
     */
    static final class EntradasEnMemoria implements EntradaDiarioRepositorio {

        final Map<UUID, EntradaDiario> entradas = new LinkedHashMap<>();
        EntradaDiario alGuardar;

        void agregar(EntradaDiario entrada) {
            entradas.put(entrada.id(), entrada);
        }

        @Override
        public boolean guardarSiNoExiste(EntradaDiario entrada) {
            if (alGuardar != null) {
                agregar(alGuardar);
                alGuardar = null;
            }
            boolean choca = entradas.containsKey(entrada.id()) || entradas.values().stream()
                    .anyMatch(otra -> otra.obraId().equals(entrada.obraId()) && otra.fecha().equals(entrada.fecha()));
            if (choca) {
                return false;
            }
            agregar(entrada);
            return true;
        }

        @Override
        public Optional<EntradaDiario> buscarPorId(UUID id) {
            return Optional.ofNullable(entradas.get(id));
        }

        @Override
        public Optional<EntradaDiario> buscarPorObraYFecha(UUID obraId, LocalDate fecha) {
            return entradas.values().stream()
                    .filter(entrada -> entrada.obraId().equals(obraId) && entrada.fecha().equals(fecha))
                    .findFirst();
        }

        @Override
        public List<EntradaDiario> listar(UUID obraId, LocalDate desde, LocalDate hasta) {
            return entradas.values().stream()
                    .filter(entrada -> entrada.obraId().equals(obraId))
                    .filter(entrada -> !entrada.fecha().isBefore(desde) && !entrada.fecha().isAfter(hasta))
                    .sorted(Comparator.comparing(EntradaDiario::fecha).reversed())
                    .toList();
        }
    }
}
