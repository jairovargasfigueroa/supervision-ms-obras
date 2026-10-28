# Obras: contratos

**Estado:** aquí va la parte de los contratos generales (`docs/contratos/` de la carpeta general) que toca a este servicio. Lo de abajo es lo conocido hasta ahora; los detalles (rutas exactas, cuerpos, errores) se definen en el diseño de cada funcionalidad.

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
| Finanzas | Nada por REST: el contrato se registra por eventos (`ObraRegistrada` → `ContratoRegistrado`) |
| Materiales | Nada: la comparación de material usado frente a previsto la arma el gateway con datos de ambos servicios |

## Eventos y comandos (RabbitMQ)

| Mensaje | Dirección | Notas |
|---|---|---|
| ObraRegistrada | publica → Finanzas | al registrar la obra, con los datos del contrato |
| ContratoRegistrado / ContratoRechazado | escucha ← Finanzas | guarda `contrato_id` y pasa a `EN_EJECUCION` |
| Iniciar saga: reservar saldo | publica → Finanzas | paso 2 |
| Saldo reservado / sin saldo | escucha ← Finanzas | |
| Cerrar consumos del periodo | publica → Materiales | paso 3 |
| Consumos cerrados / fallo | escucha ← Materiales | |
| Emitir cobro | publica → Finanzas | paso 4 |
| Cobro emitido / fallo | escucha ← Finanzas | |
| Compensaciones (reabrir consumos, liberar reserva) | publica | en orden inverso |
| Planilla aprobada | publica | para reportes (capa 4) |

## Herramientas MCP (capa 5, solo lectura)

`avance_obra`, `buscar_diario`, `planillas_estado`, `asistencia_obra` (si existe asistencia).
