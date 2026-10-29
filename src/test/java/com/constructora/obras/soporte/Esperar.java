package com.constructora.obras.soporte;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/** Espera a que algo asíncrono ocurra (outbox, consumidores), sin dormir más de lo necesario. */
public final class Esperar {

    private Esperar() {
    }

    public static void hasta(Duration limite, BooleanSupplier condicion) {
        Instant fin = Instant.now().plus(limite);
        while (!condicion.getAsBoolean()) {
            if (Instant.now().isAfter(fin)) {
                throw new AssertionError("No se cumplió la condición en " + limite.toSeconds() + " s");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrumpido", e);
            }
        }
    }
}
