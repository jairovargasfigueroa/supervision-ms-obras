# 008 · Revisar planilla y saga · tareas de `services/obras`

Requisitos y diseño: `docs/specs/008-revisar-planilla-saga/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Migración `saga_aprobacion` (con `vence_en`)
- [ ] Endpoints del portal: `aprobar` (crea saga, envía `ReservarSaldo`) y `observar`; filtros del rol `cliente` en lecturas
- [ ] Orquestador: manejo de cada respuesta, avance de pasos, compensaciones y `PlanillaAprobada` / `PlanillaObservada`
- [ ] Tarea cada 10 s para pasos vencidos; manejo de respuestas tardías
- [ ] Pruebas de la máquina de estados con bus simulado (camino feliz, fallo en cada paso, vencimiento, repetidos, tardíos)
