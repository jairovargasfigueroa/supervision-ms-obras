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
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK, creado en el celular |
| obra_id | uuid | FK obra |
| fecha | date | |
| clima | varchar | |
| texto | text | |
| registrado_por | uuid | ref Keycloak |

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
| Campo | Tipo | Notas |
|---|---|---|
| planilla_id | uuid | PK, FK planilla |
| foto_id | uuid | PK, FK foto_avance |

### saga_aprobacion
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| planilla_id | uuid | FK planilla |
| paso_actual | varchar | |
| estado | varchar | `EN_CURSO`, `COMPLETADA`, `COMPENSADA` |
| detalle | jsonb | `reservaId`, `cobroId`, motivo del fallo |
| vence_en | timestamptz | límite para la respuesta del paso actual (60 s) |
| actualizada_en | timestamptz | |

### outbox_evento
| Campo | Tipo | Notas |
|---|---|---|
| id | uuid | PK; es el `id` del mensaje (sobre) |
| tipo | varchar | `ObraRegistrada`, …; la clave es `evento.obras.<tipo>` |
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
- planilla 1 — 0..1 saga_aprobacion
