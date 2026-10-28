# 401 · Servicio de IA y base de reportes · tareas de `services/obras`

Requisitos y diseño: `docs/specs/401-base-reportes/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Migración Flyway `obra.zona` y enum `Zona` en el dominio
- [ ] `POST /obras` acepta `zona` opcional (400 si no es válida); `GET /obras` y `GET /obras/{id}` la devuelven; pruebas
- [ ] `ObraRegistrada` agrega `ubicacion` y `zona` en `datos`; prueba del outbox
