# 501 · Servidores MCP · tareas de `services/obras`

Requisitos y diseño: `docs/specs/501-servidores-mcp/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Agregar el BOM `spring-ai-bom` 2.0.2 y `spring-ai-starter-mcp-server-webmvc` (trae el SDK oficial `io.modelcontextprotocol.sdk:mcp` 2.1.0); servidor en `/mcp` sin estado (`spring.ai.mcp.server.protocol=STATELESS`), sin recursos ni *prompts*
- [x] Seguridad: `/mcp` exige el rol `agente_ia`; visibilidad de `agente_ia` igual a `gerente`; pruebas 401 / 403 / 200
- [x] Migración `planilla.presentada_en` y llenado al presentar (007); pruebas al presentar y al volver a presentar
- [x] Adaptador `mcp/HerramientasMcpObras`: `avance_obra` (con partidas y material previsto opcional, `truncado` y anotación `readOnlyHint`), reutilizando los casos de uso de 006
- [x] `buscar_diario` (rango máximo 62 días, búsqueda con escape de `%` y `_`, `truncado` de la lista y de cada texto) y `planillas_estado` (con `diasDesdePresentacion` y `truncado`), ambas con `readOnlyHint`
- [x] Errores de herramienta (`ARGUMENTO_INVALIDO`, `OBRA_NO_ENCONTRADA`) y registro JSON de cada llamada con `X-Request-Id`
- [ ] Pruebas: `tools/list` exacto, cifras iguales al REST, argumentos maliciosos, y prueba de solo lectura (ninguna tabla cambia)
