# 002 · Registrar obra · tareas de `services/obras`

Requisitos y diseño: `docs/specs/002-registrar-obra/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Migración Flyway: tablas `obra`, `partida`, `insumo_previsto`, `outbox_evento`, `mensaje_procesado` (con `motivo_rechazo_contrato`)
- [x] Dominio: agregado `Obra` con `Partida` e `InsumoPrevisto` y la regla del presupuesto (tolerancia 0,01); pruebas unitarias
- [x] Caso de uso `RegistrarObra` + puerto de repositorio + adaptador JPA
- [x] Publicador del outbox (cada 1 s) hacia el exchange `plataforma` con el sobre común
- [x] `POST /obras` con validación y errores Problem Details (`PRESUPUESTO_NO_COINCIDE`, `CODIGO_PARTIDA_REPETIDO`, `INSUMO_REPETIDO`, `CONTRATO_INVALIDO`)
- [x] `GET /obras/{id}` básico (datos, contrato, partidas, estado, motivo de rechazo) con visibilidad por rol
- [ ] Consumidor de `ContratoRegistrado` y `ContratoRechazado` idempotente con `mensaje_procesado`
- [ ] Pruebas de API e integración del outbox y del consumidor
