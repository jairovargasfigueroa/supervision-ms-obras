# Servicio Obras

Microservicio dueño de las obras, el presupuesto por partidas, las fotos de avance, el diario de obra y las planillas de avance. **Orquesta la saga «aprobar planilla».**

Parte de la «Plataforma de supervisión de obras». Si existe `../../CLAUDE.md` (carpeta que agrupa todos los proyectos), léelo primero: tiene las reglas transversales, el plan y los contratos generales.

**Estado:** esqueleto. No implementes una funcionalidad sin su especificación aprobada en `docs/specs/`.

## Stack

- Java 21 · Spring Boot 4.1 (Maven, wrapper incluido)
- PostgreSQL · Flyway (migraciones en `src/main/resources/db/migration`) · JPA con `ddl-auto: validate`
- RabbitMQ (AMQP) para la saga · OAuth2 Resource Server (tokens de Keycloak)
- Puerto: `8081` (`OBRAS_PORT`)

## Comandos

```sh
./mvnw spring-boot:run     # levantar (Windows: mvnw.cmd)
./mvnw test                # pruebas unitarias
./mvnw verify              # todas, con las de integración (*IT, Testcontainers: necesita Docker)
./mvnw package             # compilar el .jar
```

Configuración local: copiar `.env.properties.example` a `.env.properties` y completar. **`.env.properties` nunca se sube.**

Datos de prueba (011, `docs/datos-prueba.md` de `supervision-docs`): perfil `semilla`, sin web ni mensajería; carga y termina. Lo llama `infra/semilla/` con estas líneas exactas, desde esta carpeta (detalle en el README, «Datos de prueba»):

```sh
SEMILLA_FECHA=AAAA-MM-DD ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=semilla                       # cargar
SEMILLA_FECHA=AAAA-MM-DD SEMILLA_MODO=recargar ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=semilla  # vaciar y cargar
```

En Windows: `$env:SEMILLA_FECHA = 'AAAA-MM-DD'; .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=semilla"` (y `$env:SEMILLA_MODO = 'recargar'`). Lee `.env.properties`; código de salida distinto de 0 si falla o la guarda la rechaza; imprime `SEMILLA_RESUMEN servicio=obras fecha=… obras=3 partidas=21 … fotos=10 planillas=5 …`. Los datos están en `src/main/resources/semilla/obras.json` (copia de `docs/datos-prueba.md`: si cambia, se cambia allí primero). Si una capa agrega tablas o campos a Obras, amplía esta semilla en el mismo trabajo.

## Arquitectura: DDD + Hexagonal

Paquete raíz `com.constructora.obras`. Estructura prevista por contexto (`obra`, `planilla`, `avance`):

```
<contexto>/
  dominio/        entidades, objetos de valor, reglas; sin Spring ni JPA
  aplicacion/     casos de uso y puertos (interfaces de entrada y salida)
  infraestructura/
    web/          controladores REST (adaptador de entrada)
    persistencia/ entidades JPA y repositorios (adaptador de salida)
    mensajeria/   publicación y consumo de RabbitMQ (adaptador)
```

- El dominio no depende de ningún framework. Las reglas de la planilla (cálculo, límite contra lo contratado, estados) viven en `dominio`.
- Agregados: `Obra` (con sus partidas e insumos previstos) y `Planilla` (con sus líneas). Se referencian por id, no por objeto.

## Reglas del proyecto

- Lo que este servicio guarda está en `docs/datos.md`; lo que expone y escucha, en `docs/contratos.md`.
- Las fotos y registros del diario llegan con un id creado en el celular: si ya existe, se devuelve el registro existente.
- Los eventos se escriben en `outbox_evento` dentro de la misma transacción.
- La planilla usa bloqueo optimista (`version`).
- Cada usuario solo ve sus obras (residente, maestro, almacenero o cliente asignado); se valida aquí, no solo en el gateway.

## Documentación

- `docs/estructura.md`: **librerías permitidas y estructura exacta de carpetas y archivos. Obligatorio leerlo antes de escribir código.**
- `docs/servicio.md`: responsabilidades, casos de uso y qué no hace.
- `docs/datos.md`: tablas.
- `docs/contratos.md`: REST, eventos y herramientas MCP.
- `docs/specs/`: especificaciones por funcionalidad (`requisitos.md`, `diseno.md`, `tareas.md`).

## Commits

Conventional Commits en español e imperativo (`feat(planillas): agrega cálculo del neto`). Sin coautoría ni firmas de la IA.

Git Flow: ramas `feat/`, `fix/`, `docs/`… desde `develop`, que entran por *pull request*; nunca hagas *push* a `main` ni a `develop` (están protegidas). El agente crea y fusiona los PR (merge normal, sin squash) hacia `develop` cuando todas las pruebas pasan. Detalle y definición de terminado: `docs/flujo-trabajo.md` del repo `supervision-docs`.
