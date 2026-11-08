package com.constructora.obras.soporte;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas *IT: aplicación completa, PostgreSQL y RabbitMQ en Testcontainers, tokens de TokensPrueba y
 * el almacenamiento de archivos en memoria (AlmacenArchivosEnMemoria) en lugar de S3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ContenedoresPrueba.class, AlmacenArchivosPrueba.class})
public abstract class PruebaIntegracion {

    @DynamicPropertySource
    static void clavePublicaDePrueba(DynamicPropertyRegistry propiedades) {
        propiedades.add("obras.seguridad.clave-publica",
                TokensPrueba::ubicacionClavePublica);
    }
}
