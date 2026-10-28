# 103 · Registrar entrada del diario de obra · tareas de `services/obras`

Requisitos y diseño: `docs/specs/103-diario-obra/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Migración de `entrada_diario`: `registrado_en`, único (`obra_id`, `fecha`)
- [ ] Dominio: agregado `EntradaDiario`, enumeración `Clima` y reglas de texto y fecha (hora de La Paz)
- [ ] Caso de uso `RegistrarEntradaDiario` con idempotencia por `id` y `409` por día repetido (también ante la restricción única)
- [ ] `POST /obras/{obraId}/diario` (maestro asignado) y `GET /obras/{obraId}/diario` (maestro y residente asignados, admin, gerente)
- [ ] Pruebas de la spec
