# 501 · Servidores MCP · tareas de `services/obras`

Requisitos y diseño: `docs/specs/501-servidores-mcp/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Agregar el BOM `spring-ai-bom` 2.0.x y `spring-ai-starter-mcp-server-webmvc`; servidor en `/mcp` en modo sin estado
- [ ] Seguridad: `/mcp` exige el rol `agente_ia`; visibilidad de `agente_ia` igual a `gerente`; pruebas 401 / 403 / 200
- [ ] Migración `planilla.presentada_en` y llenado al presentar (007); pruebas al presentar y al volver a presentar
- [ ] Adaptador `mcp/HerramientasMcpObras`: `avance_obra` (con partidas y material previsto opcional), reutilizando los casos de uso de 006
- [ ] `buscar_diario` (rango máximo 62 días, búsqueda con escape de `%` y `_`) y `planillas_estado` (con `diasDesdePresentacion`)
- [ ] Errores de herramienta (`ARGUMENTO_INVALIDO`, `OBRA_NO_ENCONTRADA`) y registro JSON de cada llamada con `X-Request-Id`
- [ ] Pruebas: `tools/list` exacto, cifras iguales al REST, argumentos maliciosos, y prueba de solo lectura (ninguna tabla cambia)
