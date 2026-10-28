# 303 · Rostros difuminados · tareas de `services/obras`

Requisitos y diseño: `docs/specs/303-rostros-difuminados/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Migración Flyway: `foto_avance.rostros_difuminados` y `foto_avance.version_cliente_en`
- [ ] `POST /obras/{obraId}/fotos/{fotoId}/url-subida-cliente` y `PUT /obras/{obraId}/fotos/{fotoId}/version-cliente` (clave exacta, `HeadObject`, residente asignado); pruebas
- [ ] Regla en el dominio: `Planilla.presentar` exige versión para el cliente en todas las fotos adjuntas (`422 FOTO_SIN_VERSION_CLIENTE` con `errores[]`); pruebas
- [ ] `GET /obras/{obraId}/fotos`: URL de la versión para el cliente al rol `cliente` (con la excepción de las fotos anteriores a la capa 3) y `urlDescargaCliente` / `rostrosDifuminados` a los internos; pruebas
