package com.constructora.obras.avance.aplicacion;

import com.constructora.obras.avance.aplicacion.puerto.salida.FotoAvanceRepositorio;
import com.constructora.obras.avance.dominio.FotoAvance;
import com.constructora.obras.compartido.dominio.Dinero;
import com.constructora.obras.compartido.dominio.Pagina;
import com.constructora.obras.compartido.dominio.Paginacion;
import com.constructora.obras.obra.aplicacion.puerto.entrada.FiltroObras;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Asignaciones;
import com.constructora.obras.obra.dominio.ContratoCopia;
import com.constructora.obras.obra.dominio.EstadoObra;
import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.obra.dominio.Partida;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Dobles a mano de los puertos que usan los casos de uso de fotos de avance. */
final class DoblesAvance {

    private DoblesAvance() {
    }

    /** Obra en ejecución con una partida y las asignaciones indicadas. */
    static Obra obra(Asignaciones asignaciones) {
        Partida partida = Partida.nueva("P-01", "Losa", "m2", BigDecimal.TEN, BigDecimal.valueOf(100), null,
                List.of());
        return Obra.reconstruir(UUID.randomUUID(), "Obra de prueba", "Santa Cruz", LocalDate.of(2026, 11, 1),
                EstadoObra.EN_EJECUCION, asignaciones,
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

    static final class FotosEnMemoria implements FotoAvanceRepositorio {

        final Map<UUID, FotoAvance> fotos = new LinkedHashMap<>();

        @Override
        public void guardar(FotoAvance foto) {
            fotos.put(foto.id(), foto);
        }

        @Override
        public Optional<FotoAvance> buscarPorId(UUID id) {
            return Optional.ofNullable(fotos.get(id));
        }

        @Override
        public List<FotoAvance> buscarPorIds(Collection<UUID> ids) {
            return ids.stream().map(fotos::get).filter(Objects::nonNull).toList();
        }

        @Override
        public void publicar(Collection<UUID> ids) {
            ids.forEach(id -> fotos.computeIfPresent(id, (clave, foto) -> new FotoAvance(foto.id(), foto.obraId(),
                    foto.partidaId(), foto.claveArchivo(), true, foto.registradoPor(), foto.registradoEn())));
        }

        /** Solo ordena por registradoEn (y por id para desempatar), como el adaptador JPA. */
        @Override
        public Pagina<FotoAvance> listar(UUID obraId, UUID partidaId, boolean soloPublicadas,
                Paginacion paginacion) {
            Comparator<FotoAvance> orden = Comparator.comparing(FotoAvance::registradoEn)
                    .thenComparing(FotoAvance::id);
            List<FotoAvance> todas = fotos.values().stream()
                    .filter(foto -> foto.obraId().equals(obraId))
                    .filter(foto -> partidaId == null || foto.partidaId().equals(partidaId))
                    .filter(foto -> !soloPublicadas || foto.publicadaCliente())
                    .sorted(paginacion.ascendente() ? orden : orden.reversed())
                    .toList();
            List<FotoAvance> pagina = todas.stream()
                    .skip((long) (paginacion.pagina() - 1) * paginacion.tamano())
                    .limit(paginacion.tamano())
                    .toList();
            return new Pagina<>(pagina, todas.size(), paginacion.pagina(), paginacion.tamano());
        }
    }
}
