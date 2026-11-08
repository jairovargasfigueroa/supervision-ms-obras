package com.constructora.obras.compartido.infraestructura;

import com.constructora.obras.compartido.dominio.Reloj;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Reloj real del servidor (en UTC; la fecha del negocio la calcula Reloj.hoy() en America/La_Paz). */
@Component
public class RelojSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
