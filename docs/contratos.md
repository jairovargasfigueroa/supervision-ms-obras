# Obras: contratos

**Estado: pendiente.** Aquí va la parte de los contratos generales (`docs/contratos/` de la carpeta general) que toca a este servicio. Lo de abajo es lo conocido hasta ahora; los detalles (rutas exactas, cuerpos, errores) se definen en el diseño de cada funcionalidad.

## REST que expone

| Operación | Quién llama | Etapa |
|---|---|---|
| Crear obra con presupuesto por partidas | Gateway | Base |
| Listar obras / ver detalle y avance | Gateway, portal | Base |
| Registrar foto de avance (id del celular) | App de obra, Sincronización | Base |
| Crear, editar y presentar planilla | Gateway | Base |
| Aprobar u observar planilla | Portal | Base |
| Registrar entrada del diario (id del celular) | App de obra, Sincronización | Capa 1 |

## REST que consume

| Servicio | Para qué |
|---|---|
| Finanzas | Obtener los datos del contrato al registrar la obra **[por confirmar: REST o evento]** |
| Materiales | Material usado por partida y periodo, para comparar con lo previsto **[por confirmar: lo une el gateway]** |

## Eventos y comandos (RabbitMQ)

| Mensaje | Dirección | Notas |
|---|---|---|
| Iniciar saga: reservar saldo | publica → Finanzas | paso 2 |
| Saldo reservado / sin saldo | escucha ← Finanzas | |
| Cerrar consumos del periodo | publica → Materiales | paso 3 |
| Consumos cerrados / fallo | escucha ← Materiales | |
| Emitir cobro | publica → Finanzas | paso 4 |
| Cobro emitido / fallo | escucha ← Finanzas | |
| Compensaciones (reabrir consumos, liberar reserva) | publica | en orden inverso |
| Planilla aprobada | publica | para reportes (capa 4) |

## Herramientas MCP (capa 5, solo lectura)

`avance_obra`, `buscar_diario`, `asistencia_obra` (si existe asistencia).
