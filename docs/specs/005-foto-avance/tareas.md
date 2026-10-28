# 005 · Registrar foto de avance · tareas de `services/obras`

Requisitos y diseño: `docs/specs/005-foto-avance/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Cliente S3 (AWS SDK v2) configurable para RustFS
- [ ] Migración `foto_avance`; `POST /obras/{obraId}/fotos/url-subida` y `POST /obras/{obraId}/fotos` idempotente
- [ ] `GET /obras/{obraId}/fotos` con URL de descarga firmada y filtro para el cliente
- [ ] Pruebas de la spec
