package com.constructora.obras.diario.aplicacion.servicio;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.dominio.Reloj;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.diario.aplicacion.puerto.entrada.RegistrarEntradaDiarioCasoUso;
import com.constructora.obras.diario.aplicacion.puerto.salida.EntradaDiarioRepositorio;
import com.constructora.obras.diario.dominio.EntradaDiario;
import com.constructora.obras.diario.dominio.EntradaDiario.Contenido;
import com.constructora.obras.obra.aplicacion.puerto.entrada.ConsultarObraCasoUso;
import com.constructora.obras.obra.aplicacion.puerto.salida.ObraRepositorio;
import com.constructora.obras.obra.dominio.Obra;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro idempotente de entradas del diario (103, docs/specs/103-diario-obra/diseno.md). */
@Service
public class RegistrarEntradaDiarioServicio implements RegistrarEntradaDiarioCasoUso {

    private final ObraRepositorio obras;
    private final EntradaDiarioRepositorio entradas;
    private final Reloj reloj;

    public RegistrarEntradaDiarioServicio(ObraRepositorio obras, EntradaDiarioRepositorio entradas, Reloj reloj) {
        this.obras = obras;
        this.entradas = entradas;
        this.reloj = reloj;
    }

    /**
     * Orden: formato de clima y texto (DATOS_INVALIDOS) → obra visible (OBRA_NO_ENCONTRADA) → maestro de esa obra
     * (SIN_PERMISO) → id ya registrado en la obra: se devuelve el guardado, aunque el cuerpo sea distinto → otra
     * entrada de la obra ese día (DIARIO_DIA_YA_REGISTRADO) → obra en ejecución y fecha (EntradaDiario.registrar)
     * → guardar. Si la base la rechaza porque otra petición la guardó a la vez, se responde igual: con el mismo id,
     * la guardada; si no, DIARIO_DIA_YA_REGISTRADO. Un id que ya existe en otra obra también es
     * DIARIO_DIA_YA_REGISTRADO (ese registro no se puede crear ni devolver).
     */
    @Override
    @Transactional
    public Registro registrar(Comando comando, Usuario usuario) {
        Contenido contenido = EntradaDiario.validarContenido(comando.clima(), comando.texto());
        Obra obra = obraDondeRegistra(comando.obraId(), usuario);
        Optional<Registro> repetida = guardadaEnLaObra(comando.id(), obra.id());
        if (repetida.isPresent()) {
            return repetida.get();
        }
        if (entradas.buscarPorObraYFecha(obra.id(), comando.fecha()).isPresent()) {
            throw diaYaRegistrado();
        }
        // obraDondeRegistra comparó el sub con el maestro asignado (UUID): aquí ya es un UUID válido
        EntradaDiario entrada = EntradaDiario.registrar(comando.id(), obra, comando.fecha(), contenido,
                UUID.fromString(usuario.id()), reloj.ahora());
        if (!entradas.guardarSiNoExiste(entrada)) {
            return guardadaEnLaObra(comando.id(), obra.id())
                    .orElseThrow(RegistrarEntradaDiarioServicio::diaYaRegistrado);
        }
        return new Registro(entrada, true);
    }

    /** La entrada con ese id, si ya existe en esta obra; si existe en otra, DIARIO_DIA_YA_REGISTRADO. */
    private Optional<Registro> guardadaEnLaObra(UUID id, UUID obraId) {
        Optional<EntradaDiario> existente = entradas.buscarPorId(id);
        if (existente.isPresent() && !existente.get().obraId().equals(obraId)) {
            throw diaYaRegistrado();
        }
        return existente.map(entrada -> new Registro(entrada, false));
    }

    /** La obra existe y el usuario la ve (si no, 404) y es su maestro (si no, 403). */
    private Obra obraDondeRegistra(UUID obraId, Usuario usuario) {
        Obra obra = obras.buscarPorId(obraId)
                .filter(encontrada -> encontrada.esVisiblePara(usuario))
                .orElseThrow(() -> new ReglaNegocioException(ConsultarObraCasoUso.OBRA_NO_ENCONTRADA,
                        "Obra no encontrada"));
        if (!obra.puedeRegistrarDiario(usuario)) {
            throw new ReglaNegocioException(SIN_PERMISO, "Tu rol no permite esta acción.");
        }
        return obra;
    }

    private static ReglaNegocioException diaYaRegistrado() {
        return new ReglaNegocioException(EntradaDiario.DIARIO_DIA_YA_REGISTRADO,
                "Ya existe una entrada del diario para ese día");
    }
}
