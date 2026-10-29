package com.constructora.obras;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de docs/estructura.md: DDD + Hexagonal. */
@AnalyzeClasses(packages = "com.constructora.obras", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    /** El dominio no importa nada de Spring, JPA, Jackson ni de las otras capas. */
    @ArchTest
    static final ArchRule dominioSinFrameworks = noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
                    "com.fasterxml.jackson..", "tools.jackson..", "org.hibernate..",
                    "..aplicacion..", "..infraestructura..");

    /**
     * La aplicación solo depende del dominio y de sus puertos; de Spring usa únicamente las transacciones y la
     * anotación de servicio.
     */
    @ArchTest
    static final ArchRule aplicacionSinInfraestructura = noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infraestructura..", "jakarta.persistence..", "com.fasterxml.jackson..", "tools.jackson..",
                    "org.springframework.web..", "org.springframework.amqp..", "org.springframework.security..",
                    "org.springframework.data..", "org.hibernate..")
            .allowEmptyShould(true);

    /** Los controladores llaman a los casos de uso: no usan los repositorios ni las entidades JPA. */
    @ArchTest
    static final ArchRule webNoUsaPersistencia = noClasses().that().resideInAPackage("..infraestructura.web..")
            .should().dependOnClassesThat().resideInAnyPackage("..infraestructura.persistencia..",
                    "jakarta.persistence..")
            .allowEmptyShould(true);
}
