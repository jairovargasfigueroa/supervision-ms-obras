package com.constructora.obras.diario.infraestructura.persistencia;

import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.diario.dominio.EntradaDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de EntradaDiarioRepositorio sobre JPA. Corre dentro de la transacción del caso de uso. */
@Repository
public class EntradaDiarioRepositorioJpa implements EntradaDiarioRepositorio {

    private final EntradaDiarioJpaRepository jpa;
    private final EntradaDiarioPersistenciaMapper mapper;

    public EntradaDiarioRepositorioJpa(EntradaDiarioJpaRepository jpa, EntradaDiarioPersistenciaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public boolean guardarSiNoExiste(EntradaDiario entrada) {
        return jpa.insertarSiNoExiste(entrada.id(), entrada.obraId(), entrada.fecha(), entrada.clima().name(),
                entrada.texto(), entrada.registradoPor(), entrada.registradoEn()) == 1;
    }

    @Override
    public Optional<EntradaDiario> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapper::aDominio);
    }

    @Override
    public Optional<EntradaDiario> buscarPorObraYFecha(UUID obraId, LocalDate fecha) {
        return jpa.findByObraIdAndFecha(obraId, fecha).map(mapper::aDominio);
    }

    @Override
    public List<EntradaDiario> listar(UUID obraId, LocalDate desde, LocalDate hasta) {
        return jpa.findByObraIdAndFechaBetweenOrderByFechaDesc(obraId, desde, hasta).stream()
                .map(mapper::aDominio).toList();
    }

    @Override
    public List<EntradaDiario> buscar(UUID obraId, LocalDate desde, LocalDate hasta, String texto, int limite) {
        return jpa.buscarPorTexto(obraId, desde, hasta, patron(texto), limite).stream()
                .map(mapper::aDominio).toList();
    }

    @Override
    public long contar(UUID obraId, LocalDate desde, LocalDate hasta, String texto) {
        return jpa.contarPorTexto(obraId, desde, hasta, patron(texto));
    }

    /**
     * Patrón de ILIKE «contiene»: el texto va como parámetro y sus \, % y _ se escapan con \ para que sean literales.
     * Sin texto, '%' (todas: texto no admite nulos).
     */
    static String patron(String texto) {
        if (texto == null) {
            return "%";
        }
        return "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
