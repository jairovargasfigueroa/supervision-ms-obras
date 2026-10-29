# 006 · Consultar obra · tareas de `services/obras`

Requisitos y diseño: `docs/specs/006-consultar-obra/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Dominio: `avance()`, `plazoTranscurrido(hoy)`, `enRiesgo` con reloj inyectable; pruebas en los bordes
- [x] `GET /obras` con filtros, indicadores y `ultimaPlanilla`; visibilidad por rol
- [x] Completar `GET /obras/{id}` con `ejecutadaAcumulada` y `avancePct` por partida
- [x] `GET /obras/{id}/material-previsto`
- [ ] Pruebas de la spec
