# 301 · Detección de casco y chaleco · tareas de `services/obras`

Requisitos y diseño: `docs/specs/301-deteccion-epp/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Migración Flyway: `foto_avance.sin_chaleco` y `foto_avance.version_modelo_epp`
- [ ] Objeto de valor `DeteccionEpp` en el dominio con sus validaciones; si no son válidas, la foto se registra sin detección y con el aviso `DETECCION_DESCARTADA`; pruebas
- [ ] `POST /obras/{obraId}/fotos` acepta `deteccion` opcional sin romper la idempotencia; `GET /obras/{obraId}/fotos` la devuelve (`null` para `cliente`); pruebas de la spec
