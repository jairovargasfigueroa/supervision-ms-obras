# Obras: estructura y librerías

Obligatorio para todo el código de este servicio. Si algo no encaja, propón el cambio antes de salirte de esta estructura.

## Librerías

Starters de Spring Boot 4.1 (web MVC, security OAuth2 resource server, data JPA, AMQP, validation, actuator), Flyway, MapStruct 1.6.3, `springdoc-openapi-starter-webmvc-ui` 3.2 (Swagger UI en `/docs`, solo perfil `dev`), AWS SDK v2 `s3`, y para pruebas JUnit 5 + AssertJ, Testcontainers 2 (PostgreSQL y RabbitMQ) y ArchUnit 1.5. Sin Lombok: DTO y objetos de valor son `record`.

## Árbol

```
src/main/java/com/constructora/obras/
  ObrasApplication.java
  compartido/                      # lo que usan todos los contextos
    dominio/        ReglaNegocioException.java, Dinero.java (redondeo HALF_UP a 2), Periodo.java (AAAA-MM), Reloj.java (puerto de la hora)
    infraestructura/
      seguridad/    ConfiguracionSeguridad.java (rutas, JwtDecoder con emisor y audiencia), ConvertidorRolesKeycloak.java (realm_access.roles → ROLE_<rol>), RolesEfectivos.java (regla del azp), UsuarioActual.java
      web/          ManejadorErrores.java (ProblemDetail con "codigo" y "errores"), RespuestaProblema.java (401/403 desde los filtros), FiltroRequestId.java, SaludController.java
      mensajeria/   ConfiguracionRabbit.java, Sobre.java, PublicadorOutbox.java, OutboxEventoEntity.java, MensajeProcesadoEntity.java
      archivos/     ConfiguracionS3.java, AlmacenArchivosS3.java
  obra/                            # un paquete por contexto: obra, avance, planilla, saga, diario (capa 1)
    dominio/        Obra.java, Partida.java, InsumoPrevisto.java, EstadoObra.java, Asignaciones.java, ContratoCopia.java
    aplicacion/
      puerto/entrada/   RegistrarObraCasoUso.java, ConsultarObraCasoUso.java
      puerto/salida/    ObraRepositorio.java, PublicadorEventos.java
      servicio/         RegistrarObraServicio.java, ConsultarObraServicio.java
    infraestructura/
      web/              ObraController.java, dto/RegistrarObraRequest.java, dto/ObraResponse.java, ObraWebMapper.java
      persistencia/     ObraEntity.java, PartidaEntity.java, InsumoPrevistoEntity.java, ObraJpaRepository.java, ObraRepositorioJpa.java, ObraPersistenciaMapper.java
      mensajeria/       ContratoRegistradoConsumidor.java
src/main/resources/
  application.yaml, application-dev.yaml, application-test.yaml
  db/migration/V1__obra.sql, V2__planilla.sql, ...        # una migración por funcionalidad, nunca se edita una ya aplicada
src/test/java/com/constructora/obras/
  obra/dominio/ObraTest.java                    # unitarias del dominio, sin Spring
  obra/aplicacion/RegistrarObraServicioTest.java # con dobles de los puertos
  obra/infraestructura/web/ObraControllerIT.java # @SpringBootTest + Testcontainers + tokens de prueba
  soporte/                                      # PruebaIntegracion (base de los *IT), ContenedoresPrueba, TokensPrueba, ControladorPruebaRoles (endpoints solo de prueba)
  compartido/infraestructura/seguridad/SeguridadIT.java
  ArquitecturaTest.java                         # ArchUnit
```

## Reglas

- `dominio` no importa nada de Spring, JPA, Jackson ni de `infraestructura`. Lo verifica `ArquitecturaTest`.
- `aplicacion` solo depende de `dominio` y de sus puertos. Los servicios llevan `@Transactional`.
- Controladores delgados: validan el DTO (`@Valid`), llaman al caso de uso y mapean con MapStruct.
- Entidades JPA separadas del dominio (`*Entity`), mapeadas con MapStruct en `*PersistenciaMapper`.
- Las reglas del negocio lanzan `ReglaNegocioException(codigo, mensaje)`; `ManejadorErrores` la convierte en `422` (o `409` según el código).
- Autorización con `@PreAuthorize` en el controlador; la visibilidad por obra se valida en el caso de uso con `UsuarioActual`.
- Eventos: el caso de uso llama al puerto `PublicadorEventos`, que guarda en `outbox_evento` dentro de la misma transacción.
- Nombres: clases en español (`RegistrarObraServicio`), sufijos técnicos permitidos (`Controller`, `Entity`, `Repositorio`, `Mapper`, `Test`, `IT`).
- Rutas REST en español según `docs/contratos.md`; JSON `camelCase`; columnas `snake_case`.

## Ejemplo de una funcionalidad de punta a punta

`POST /obras` → `ObraController.registrar(@Valid RegistrarObraRequest)` → `ObraWebMapper` → `RegistrarObraCasoUso.ejecutar(comando)` → `Obra.registrar(...)` valida el presupuesto → `ObraRepositorio.guardar(obra)` + `PublicadorEventos.publicar(ObraRegistrada)` → `201 ObraResponse`.

## Pruebas

- Dominio: unitarias puras, con `Reloj` fijo.
- Servicios: dobles de los puertos (sin Mockito si un doble a mano es más claro).
- API: `*IT` que extienden `PruebaIntegracion`, con Testcontainers; tokens de `TokensPrueba`, firmados con una clave RSA creada al iniciar las pruebas (la aplicación la usa por `obras.seguridad.clave-publica`).
- `./mvnw verify` corre todo.
