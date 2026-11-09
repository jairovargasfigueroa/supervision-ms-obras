# 505 · Asistente de reportes y agentes completos · tareas de `Obras`

Requisitos y diseño: `docs/specs/505-asistente-reportes/` de la carpeta que agrupa los proyectos (aprobada el 09/11/2026).

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Aplicar «Cambios a contratos y datos» de Obras: `docs/contratos.md` (`avance_obra` por lote)
- [x] `avance_obra` con `periodo` sin `obraId`: obras `EN_EJECUCION` (hasta 50) con partidas reducidas (`partidaId`, `codigo`, `nombre`, `materialPrevisto`), omitiendo las que no tienen material previsto
- [x] Pruebas: `AvanceObraMcpIT` (lote) y las demás de MCP en verde
