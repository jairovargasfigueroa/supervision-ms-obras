# Obras: datos (PostgreSQL)

Base propia: `obras_db`. Ningún otro servicio la lee. Las referencias a otros servicios («ref») son solo ids, sin clave foránea.

## Tablas del sistema base

Migración de la 002: `V1__obra.sql` (`obra`, `partida`, `insumo_previsto`, `outbox_evento`, `mensaje_procesado`). Tipos: montos `numeric(14,2)`, cantidades `numeric(14,3)`, porcentajes `numeric(5,2)`; `obra.nombre` `varchar(150)` y `obra.ubicacion` `varchar(200)` (002, criterio 2). `obra.perimetro` queda sin usar hasta la 401.

### obra
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| nombre | varchar | |
| ubicacion | varchar | |
| zona | varchar(10) | `NORTE`, `SUR`, `ESTE`, `OESTE`, `CENTRO`; null = sin zona (401, `V7__obra_zona.sql`) |
| perimetro | jsonb | polígono de la obra (opcional) |
| estado | varchar | `SIN_CONTRATO`, `EN_EJECUCION`, `CONCLUIDA` |
| cliente_usuario_id | uuid | ref Keycloak |
| residente_usuario_id | uuid | ref Keycloak |
| maestro_usuario_id | uuid | ref Keycloak |
| almacenero_usuario_id | uuid | ref Keycloak |
| contrato_id | uuid | ref Finanzas |
| monto_contrato | numeric | copia del contrato |
| plazo_dias | int | copia del contrato |
| anticipo_pct | numeric | copia del contrato |
| retencion_pct | numeric | copia del contrato |
| fecha_inicio | date | |
| motivo_rechazo_contrato | text | si Finanzas rechazó el contrato (002) |

### partida
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| obra_id | uuid | FK obra |
| codigo | varchar | |
| nombre | varchar | |
| unidad | varchar | m², m³, kg, … |
| cantidad_contratada | numeric | |
| precio_unitario | numeric | |
| meses | varchar | meses en que se ejecuta |

Único: (`obra_id`, `codigo`). Partidas e insumos se devuelven ordenados por `codigo` y por `material_id` (la tabla no guarda el orden en que se cargaron).

### insumo_previsto
| Campo | Tipo | Notas |
|---|---|---|
| partida_id | uuid | PK, FK partida |
| material_id | varchar | PK, ref Materiales |
| cantidad_por_unidad | numeric | material previsto por unidad de partida |

### foto_avance
Migración de la 005: `V2__foto_avance.sql`. Índices por (`obra_id`, `registrado_en desc`) y (`partida_id`, `registrado_en desc`) para la lista.

| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK, creado en el celular |
| obra_id | uuid | FK obra |
| partida_id | uuid | FK partida |
| clave_archivo | varchar(200) | clave en el almacenamiento de objetos: `fotos/{obraId}/{id}.jpg` |
| clave_archivo_cliente | varchar(200) | versión con rostros difuminados (capa 3); null hasta la 303 |
| personas_detectadas | int | capa 3; null hasta la 301 |
| sin_casco | int | capa 3; null hasta la 301 |
| publicada_cliente | boolean | no nulo, `false` al registrarse; pasa a `true` al presentar la planilla que la adjunta (007) |
| registrado_por | uuid | ref Keycloak (`sub` de quien la registró) |
| registrado_en | timestamptz | fecha y hora en que se tomó (`tomadaEn` del celular) |

### entrada_diario
Migración de la 103 (capa 1): `V6__entrada_diario.sql`. Único (`obra_id`, `fecha`): una entrada por obra y día; si dos llegan a la vez, la base rechaza la segunda (`409 DIARIO_DIA_YA_REGISTRADO`). El mismo índice sirve a la lista por rango de fechas. La semilla de la 011 no carga entradas; `recargar` vacía esta tabla junto con las demás (toma la lista de `pg_tables`).

| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK, creado en el celular |
| obra_id | uuid | FK obra |
| fecha | date | día de la entrada (hora de La Paz) |
| clima | varchar(20) | *check*: `SOLEADO`, `NUBLADO`, `LLUVIA_LEVE`, `LLUVIA_FUERTE` |
| texto | text | de 10 a 2000 caracteres, guardado sin los espacios de los extremos |
| registrado_por | uuid | ref Keycloak (`sub` del maestro) |
| registrado_en | timestamptz | momento en que el servidor la recibió |

### planilla
Migración de la 006: `V3__planilla.sql` (`planilla` y `linea_planilla`). La 006 solo las lee (avance, ejecutada acumulada, última planilla y material previsto); la 007 crea y guarda planillas sobre estas mismas tablas y agrega `planilla_foto` en su migración. Tipos: montos `numeric(14,2)`, `cantidad_periodo` `numeric(14,3)`, `estado` `varchar(20)` con *check* de los cinco estados, `periodo` con *check* de primer día del mes; `observacion`, `cobro_id` y `cobro_numero` admiten nulo.

| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| obra_id | uuid | FK obra |
| numero | int | correlativo por obra |
| periodo | date | primer día del mes |
| estado | varchar | `BORRADOR`, `PRESENTADA`, `APROBANDO`, `APROBADA`, `OBSERVADA` |
| subtotal | numeric | |
| descuento_anticipo | numeric | |
| retencion | numeric | |
| neto | numeric | |
| observacion | text | motivo del cliente o del fallo de la saga |
| cobro_id | uuid | ref Finanzas, al aprobarse |
| cobro_numero | varchar(20) | número del cobro (`COB-AAAA-NNNN`), copiado de `CobroEmitido` al aprobarse; lo ven todos los que ven la planilla (008) |
| version | int | bloqueo optimista |

Único: (`obra_id`, `numero`) y (`obra_id`, `periodo`).

### linea_planilla
| Campo | Tipo | Notas |
|---|---|---|
| planilla_id | uuid | PK, FK planilla |
| partida_id | uuid | PK, FK partida |
| cantidad_periodo | numeric | |
| monto | numeric | cantidad × precio unitario |

### planilla_foto
Migración de la 007: `V4__planilla_foto.sql`. Fotos de avance adjuntas como respaldo; al presentar la planilla, sus fotos pasan a `publicada_cliente = true`.

| Campo | Tipo | Notas |
|---|---|---|
| planilla_id | uuid | PK, FK planilla |
| foto_id | uuid | PK, FK foto_avance |

### saga_aprobacion
Migración de la 008: `V5__saga_aprobacion.sql`. Su `id` es el `correlacionId` de todos los mensajes de la saga. Una planilla observada por la saga se corrige, se vuelve a presentar y se puede aprobar otra vez (otra saga): hay a lo sumo **una saga `EN_CURSO` por planilla** (índice único parcial). Índice parcial por `vence_en` de las `EN_CURSO` para la tarea de vencimiento.

| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| planilla_id | uuid | FK planilla |
| paso_actual | varchar(20) | paso que espera respuesta: `RESERVAR_SALDO` (2), `CERRAR_CONSUMOS` (3) o `EMITIR_COBRO` (4); al terminar o compensar queda el último |
| estado | varchar(20) | `EN_CURSO`, `COMPLETADA`, `COMPENSADA` |
| detalle | jsonb | no nulo (`{}` al empezar): `reservaId`, `cobroId`, `cobroNumero` y `motivo` del fallo |
| vence_en | timestamptz | límite para la respuesta del paso actual (60 s desde que se envió su comando) |
| actualizada_en | timestamptz | |

### outbox_evento
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK; es el `id` del mensaje (sobre) |
| tipo | varchar | `ObraRegistrada`, `ReservarSaldo`, … |
| clave | varchar(150) | clave de enrutamiento, no nula (V5, 008): `evento.obras.<tipo>` para los eventos y `comando.<destino>.<tipo>` para los comandos de la saga (`comando.finanzas.ReservarSaldo`) |
| payload | jsonb | sobre completo (`id`, `tipo`, `version`, `ocurridoEn`, `origen`, `correlacionId`, `datos`) |
| creado_en | timestamptz | |
| publicado_en | timestamptz | nulo hasta publicarse |

### mensaje_procesado
| Campo | Tipo | Notas |
|---|---|---|
| id_mensaje | uuid | PK; `id` del mensaje recibido (respuestas de la saga, `ContratoRegistrado`) |
| procesado_en | timestamptz | |

## Tablas opcionales

`trabajador` y `asistencia` (control de asistencia), solo si se agrega esa funcionalidad.

## Relaciones

- obra 1 — 0..* partida · partida 1 — 0..* insumo_previsto
- obra 1 — 0..* foto_avance · obra 1 — 0..* entrada_diario
- obra 1 — 0..* planilla · planilla 1 — 1..* linea_planilla (cada línea mide una partida)
- planilla 1 — 0..* planilla_foto (cada una usa una foto_avance)
- planilla 1 — 0..* saga_aprobacion (a lo sumo una `EN_CURSO`; una nueva por cada vez que el cliente aprueba)
