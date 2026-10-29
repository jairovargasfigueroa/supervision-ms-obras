# Obras

Microservicio de la Plataforma de supervisión de obras: obras, presupuesto por partidas, avance, planillas y la saga «aprobar planilla».

Java 21 · Spring Boot · PostgreSQL.

```sh
cp .env.properties.example .env.properties   # completar los valores
./mvnw spring-boot:run
```

## Datos de prueba (011)

Carga la parte de Obras de `docs/datos-prueba.md` (repo `supervision-docs`): 3 obras con la copia del contrato, 21 partidas con sus insumos, 5 planillas (montos y totales calculados con la regla del dominio) y 10 fotos de avance registradas en la base (las imágenes las sube infra al bucket). Normalmente la corre `infra/semilla/semilla.ps1` (o `.sh`) junto con Finanzas y Materiales; también se puede correr sola.

Se corre **desde esta carpeta** (lee `.env.properties` como el perfil normal; las variables `OBRAS_DB_*` del entorno tienen prioridad). Necesita PostgreSQL; no necesita RabbitMQ ni Keycloak. Arranca sin servidor web (no choca con Obras corriendo en el 8081), sin consumidores ni publicador del outbox, aplica las migraciones, carga y termina.

```sh
# Linux / macOS: cargar (inserta solo lo que no existe por id; no pisa lo que cambió en una demostración)
SEMILLA_FECHA=2026-10-28 ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=semilla

# Recargar: vacía todas las tablas de obras_db (menos flyway_schema_history) y carga de nuevo
SEMILLA_FECHA=2026-10-28 SEMILLA_MODO=recargar ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=semilla
```

```powershell
# Windows (PowerShell)
$env:SEMILLA_FECHA = '2026-10-28'; .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=semilla"
$env:SEMILLA_FECHA = '2026-10-28'; $env:SEMILLA_MODO = 'recargar'; .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=semilla"
```

- `SEMILLA_FECHA` (`AAAA-MM-DD`) es la fecha de carga D; si falta, se usa hoy en `America/La_Paz`. infra la calcula una vez y la pasa a los tres servicios.
- `SEMILLA_MODO`: `cargar` (por defecto) o `recargar`. Equivale a `-Dspring-boot.run.arguments=--semilla.modo=recargar`. Recargar va en la misma transacción que la carga: si algo falla, no se borra nada.
- `-Dspring-boot.run.profiles=dev,semilla` (como en `docs/datos-prueba.md`) funciona igual.
- Al terminar escribe un resumen y una línea fija para infra: `SEMILLA_RESUMEN servicio=obras fecha=2026-10-28 modo=cargar obras=3 partidas=21 insumos=58 fotos=10 planillas=5 lineas=34 fotosAdjuntas=5` (totales en la base al terminar).
- Código de salida: `0` bien; distinto de `0` si falló (no queda nada a medias) o si la guarda la rechazó: perfil `prod` activo o base que no está en `localhost`, `127.0.0.1`, `::1` ni en el contenedor `postgres` de infra. La guarda va antes de las migraciones.
- No escribe en `outbox_evento`, `mensaje_procesado` ni `saga_aprobacion` y no publica nada en RabbitMQ.

Documentación del servicio en [`docs/`](docs/) e instrucciones para el agente en [`CLAUDE.md`](CLAUDE.md).
