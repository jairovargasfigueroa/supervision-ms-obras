package com.constructora.obras.soporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** En las pruebas *IT el puerto AlmacenArchivos es el doble en memoria, no S3. */
@TestConfiguration(proxyBeanMethods = false)
public class AlmacenArchivosPrueba {

    @Bean
    @Primary
    AlmacenArchivosEnMemoria almacenArchivosEnMemoria() {
        return new AlmacenArchivosEnMemoria();
    }
}
