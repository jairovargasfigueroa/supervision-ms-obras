# 010 · Notificaciones · tareas de `services/obras`

Requisitos y diseño: `docs/specs/010-notificaciones/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] `POST /planillas/{id}/presentar`: outbox `evento.obras.PlanillaPresentada` (`planillaId, obraId, numero, periodo, neto, reenvio, presentadaEn`) en la misma transacción; `reenvio = true` si venía de `OBSERVADA`
- [ ] `PlanillaAprobada` agrega `numero`; `PlanillaObservada` agrega `numero` y `periodo` (siguen en `version: 1`)
- [ ] Pruebas de la spec (presentar por primera vez y después de una observación; campos nuevos en los dos eventos)
