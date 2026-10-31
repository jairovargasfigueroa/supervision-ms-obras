# 401 · Servicio de IA y base de reportes · tareas de `services/obras`

Requisitos y diseño: `docs/specs/401-base-reportes/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Migración Flyway `obra.zona` y enum `Zona` en el dominio
- [x] `POST /obras` acepta `zona` opcional (400 si no es válida); `GET /obras` y `GET /obras/{id}` la devuelven; pruebas
- [x] `ObraRegistrada` agrega `ubicacion` y `zona` en `datos`; prueba del outbox
- [x] Semilla (011): `zona` de las 3 obras (`NORTE`, `ESTE`, `SUR`; `docs/datos-prueba.md`, sección 6), para que la carga inicial la lea de `GET /obras/{id}`
