# 007 · Elaborar y presentar planilla · tareas de `services/obras`

Requisitos y diseño: `docs/specs/007-elaborar-planilla/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Migración `planilla_foto` (`planilla` y `linea_planilla` ya existen: las creó la 006 en `V3__planilla.sql`, con `PlanillaEntity` y `PlanillaJpaRepository` de solo lectura para ampliar)
- [x] Dominio `Planilla`: creación, actualización de líneas con límite, cálculo de totales con redondeo, presentación; pruebas unitarias con caso fijo (anticipo 10 %, retención 7 %)
- [x] Casos de uso y endpoints: crear, listar, detalle, guardar (bloqueo optimista), presentar (publica las fotos adjuntas)
- [x] Pruebas de API de la spec
