# Obras: qué hace

## Responsabilidades

- Registrar la obra con su presupuesto por partidas y el material previsto por unidad.
- Guardar una copia de los datos del contrato que necesita para calcular la planilla (monto, plazo, anticipo, retención).
- Recibir las fotos de avance y las entradas del diario de obra.
- Calcular el avance de la obra (por partida y total) frente al plazo.
- Elaborar, presentar y corregir las planillas de avance.
- Orquestar la saga «aprobar planilla» y aplicar las compensaciones.
- Atender al portal del cliente (sus obras, fotos publicadas y planillas).

## Casos de uso

| Caso de uso | Quién | Etapa |
|---|---|---|
| Registrar obra (datos y presupuesto por partidas) | Administración | Base |
| Consultar obra (avance, fotos, planillas) | Residente, gerente, administración | Base |
| Registrar foto de avance | Maestro de obra, residente | Base |
| Elaborar y presentar planilla | Residente | Base |
| Ver obra y planillas en el portal | Cliente | Base |
| Aprobar u observar planilla (inicia la saga) | Cliente | Base |
| Registrar entrada del diario de obra | Maestro de obra | Capa 1 **[por confirmar]** |
| Herramientas MCP para los agentes | Servicio de IA | Capa 5 |

## Qué no hace

- No guarda el contrato original ni los cobros (Finanzas).
- No guarda stock ni movimientos de material (Materiales).
- No gestiona usuarios (Keycloak); solo guarda los ids de usuario asignados a cada obra.

## Reglas

Las reglas de obra, partidas, planilla y saga están en `docs/reglas-negocio.md` de la carpeta general. Resumen de lo que este servicio valida:

- Suma de partidas = monto del contrato.
- Acumulado anterior + cantidad del periodo ≤ cantidad contratada.
- Transiciones de estado de la planilla según la tabla de estados.
- Una sola planilla abierta por obra.
