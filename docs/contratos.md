# Obras: contratos

Fuente para este servicio. La copia general está en `docs/contratos/` de la carpeta que agrupa los proyectos. Errores en formato Problem Details con `codigo` (ver convenciones). Todas las rutas, salvo `/salud`, exigen token. JSON en `camelCase`.

## REST que expone (puerto 8081)

### GET /salud
Sin token · `200 {"estado":"ok"}`

Token: firma con el JWKS del realm, vencimiento, emisor `OBRAS_KEYCLOAK_ISSUER` y audiencia `plataforma-api`. Sin token o con uno inválido: `401 NO_AUTENTICADO`; si Keycloak no responde al pedir el JWKS: `503 SERVICIO_NO_DISPONIBLE`. CORS solo para `OBRAS_CORS_ORIGENES` (el portal). Ruta que no existe (con token válido): `404 RUTA_NO_ENCONTRADA`. Toda respuesta lleva `X-Request-Id` (el que llega o un UUID nuevo).

### POST /obras · 002
Roles: `admin`
```json
{
  "nombre": "Edificio Las Palmas",
  "ubicacion": "Av. Banzer 4to anillo, Santa Cruz",
  "fechaInicio": "2026-11-01",
  "asignaciones": {
    "residenteId": "uuid-keycloak", "maestroId": "uuid-keycloak",
    "almaceneroId": "uuid-keycloak", "clienteId": "uuid-keycloak"
  },
  "contrato": { "monto": 1250000.00, "plazoDias": 300, "anticipoPct": 10, "retencionPct": 7, "firmadoEn": "2026-10-20" },
  "partidas": [
    {
      "codigo": "P-01", "nombre": "Losa de hormigón armado", "unidad": "m2",
      "cantidadContratada": 850, "precioUnitario": 420.50, "meses": "nov-dic",
      "insumos": [{ "materialId": "id-material", "cantidadPorUnidad": 1.1 }]
    }
  ]
}
```
Devuelve `201` con el detalle de la obra (como `GET /obras/{id}`), `estado: "SIN_CONTRATO"`.
Errores: `400` formato · `422 PRESUPUESTO_NO_COINCIDE` · `422 CODIGO_PARTIDA_REPETIDO` · `422 INSUMO_REPETIDO` · `422 CONTRATO_INVALIDO` · `403`.
- `403 SIN_PERMISO` se comprueba antes que el cuerpo: un rol distinto de `admin` recibe 403 aunque el cuerpo esté mal formado.
- `400 DATOS_INVALIDOS` (formato): campos obligatorios, `nombre` hasta 150 y `ubicacion` hasta 200 caracteres, al menos una partida, `cantidadContratada`, `precioUnitario` y `cantidadPorUnidad` > 0, montos y porcentajes con hasta 2 decimales y cantidades con hasta 3. Trae `errores[]` con `campo` (por ejemplo `partidas[0].cantidadContratada`) y `mensaje`.
- `422`: `detail` es el texto del catálogo (`PRESUPUESTO_NO_COINCIDE`: «El presupuesto (Bs 1.249.925,00) no coincide con el monto del contrato (Bs 1.250.000,00)») y `errores[]` señala el campo (`contrato.monto`, `contrato.plazoDias`, `contrato.anticipoPct`, `contrato.retencionPct`, `partidas[i].codigo`, `partidas[i].insumos[j].materialId`, `partidas`). Se valida en este orden: contrato, códigos, insumos, presupuesto.
- `firmadoEn` no se guarda en Obras: viaja en `ObraRegistrada`.
Publica `ObraRegistrada`.

### GET /obras?estado=&busqueda= · 006, 009
Roles: todos; filtra por visibilidad (convenciones).
```json
[{
  "id": "uuid", "nombre": "Edificio Las Palmas", "ubicacion": "…", "estado": "EN_EJECUCION",
  "fechaInicio": "2026-11-01", "avancePct": 32.5, "plazoPct": 41.0, "enRiesgo": false,
  "ultimaPlanilla": { "periodo": "2027-02", "estado": "APROBADA" }
}]
```
`ultimaPlanilla` es `null` si no hay planillas.
- Devuelve las obras que el usuario ve (convenciones), ordenadas por nombre. `avancePct`, `plazoPct` y `enRiesgo` se calculan al leer con docs/reglas-negocio.md («Avance y plazo»): el avance solo cuenta las planillas `APROBADA`; el plazo usa la fecha de hoy en `America/La_Paz`. Porcentajes con 2 decimales.
- `ultimaPlanilla`: la planilla de periodo más reciente de la obra, en cualquier estado (`periodo` `AAAA-MM`). Para quien ve la obra solo como `cliente` (desde el portal, `azp = portal`) no cuenta las `BORRADOR`, igual que `GET /obras/{obraId}/planillas` (007).
- `estado` (006): uno de `SIN_CONTRATO`, `EN_EJECUCION`, `CONCLUIDA`; otro valor: `400 DATOS_INVALIDOS` con `errores[].campo = estado`. `busqueda` (006): el nombre contiene el texto (sin distinguir mayúsculas; `%` y `_` son texto literal). Vacíos (o solo espacios) no filtran; los dos se combinan.

### GET /obras/{id} · 002, 004, 006
Roles: los que pueden ver la obra.
```json
{
  "id": "uuid", "nombre": "…", "ubicacion": "…", "estado": "EN_EJECUCION",
  "motivoRechazoContrato": null, "fechaInicio": "2026-11-01",
  "asignaciones": { "residenteId": "…", "maestroId": "…", "almaceneroId": "…", "clienteId": "…" },
  "contrato": { "contratoId": "uuid|null", "monto": 1250000.00, "plazoDias": 300, "anticipoPct": 10, "retencionPct": 7 },
  "avancePct": 32.5, "plazoPct": 41.0, "enRiesgo": false,
  "partidas": [{
    "id": "uuid", "codigo": "P-01", "nombre": "…", "unidad": "m2",
    "cantidadContratada": 850, "precioUnitario": 420.50, "meses": "nov-dic",
    "ejecutadaAcumulada": 300, "avancePct": 35.29,
    "insumos": [{ "materialId": "…", "cantidadPorUnidad": 1.1 }]
  }]
}
```
Errores: `404 OBRA_NO_ENCONTRADA` (también si el usuario no puede verla) o si el id no es un UUID.
- Partidas ordenadas por `codigo`. `ejecutadaAcumulada` (3 decimales) = suma de `cantidad_periodo` de sus líneas en planillas `APROBADA`; `avancePct` de la partida = ejecutada acumulada / cantidad contratada × 100 (2 decimales). `avancePct`, `plazoPct` y `enRiesgo` de la obra, como en `GET /obras` (006). En `POST /obras` (002) las partidas salen en el orden en que llegaron y con ejecutada y avance en 0.

### GET /obras/{id}/material-previsto?periodo=AAAA-MM · 006
Roles: `admin`, `gerente` y `residente` asignado (si no ve la obra: `404 OBRA_NO_ENCONTRADA`).
`{ "periodo": "2027-02", "hayPlanilla": true, "items": [{ "partidaId": "…", "materialId": "…", "cantidadPrevista": 55.0 }] }`
- `items`: por cada partida de la obra y cada material previsto por unidad, `cantidadPrevista` = `cantidad_periodo` de la partida en la planilla de ese periodo (en cualquier estado; 0 si la planilla no tiene línea de esa partida) × `cantidad_por_unidad`, con 3 decimales (HALF_UP). Ordenados por código de partida y `materialId`; una partida sin material previsto no agrega filas.
- Sin planilla en el periodo: `hayPlanilla: false` e `items: []` (el ERP muestra «Sin planilla en este periodo»).
- Errores, en este orden: `403 SIN_PERMISO` (rol distinto de `admin`, `gerente` o `residente`) · `400 DATOS_INVALIDOS` (`periodo` falta o no es `AAAA-MM`; `errores[].campo = periodo`) · `404 OBRA_NO_ENCONTRADA` (no existe, no la ve o el id no es un UUID) · `403 SIN_PERMISO` (ve la obra con otro papel: por ejemplo, con los roles residente y maestro, asignado solo como maestro).

### POST /obras/{obraId}/fotos/url-subida · 005
Roles: `maestro`, `residente` asignados. Recibe `{ "fotoId": "uuid" }`.
Devuelve `{ "url": "https://…", "claveArchivo": "fotos/{obraId}/{fotoId}.jpg", "expiraEn": "…" }`.
- La URL es de S3, para `PUT`, vence a los 10 minutos (`expiraEn`, ISO 8601 en UTC) y lleva el `Content-Type: image/jpeg` dentro de la firma: la subida debe enviar ese mismo encabezado.
- Errores: `403 SIN_PERMISO` (otro rol, comprobado antes que el cuerpo; o un usuario que ve la obra con otro papel, por ejemplo con los roles almacenero y maestro asignado solo como almacenero) · `404 OBRA_NO_ENCONTRADA` (no existe, no la ve o el id no es un UUID) · `400 DATOS_INVALIDOS` (`fotoId` falta o no es un UUID) · `503 SERVICIO_NO_DISPONIBLE` si S3 no está configurado (`OBRAS_S3_BUCKET`, `OBRAS_S3_ACCESS_KEY` u `OBRAS_S3_SECRET_KEY` vacías; el servicio arranca igual).

### POST /obras/{obraId}/fotos · 005
Roles: `maestro`, `residente` asignados.
Recibe `{ "id": "uuid", "partidaId": "uuid", "claveArchivo": "fotos/…jpg", "tomadaEn": "2026-11-15T15:20:00Z" }`.
Devuelve `201` (o `200` si el `id` ya existía) `{ "id", "partidaId", "registradoPor", "registradoEn", "publicadaCliente": false }`.
Errores: `404 OBRA_NO_ENCONTRADA` · `422 PARTIDA_NO_ES_DE_LA_OBRA` · `422 CLAVE_ARCHIVO_INVALIDA`.
- `registradoEn` = `tomadaEn` (al microsegundo) y `registradoPor` = `sub` del token. No necesita S3 (no comprueba que el archivo exista).
- Orden: rol (`403 SIN_PERMISO`, antes que el cuerpo) → cuerpo (`400 DATOS_INVALIDOS`: `id`, `partidaId` y `tomadaEn` obligatorios; `claveArchivo` obligatoria, hasta 200 caracteres) → obra visible (`404`) → maestro o residente de esa obra (`403 SIN_PERMISO`) → `id` ya registrado: `200` con el registro guardado, sin volver a validar → partida (`422 PARTIDA_NO_ES_DE_LA_OBRA`, `errores[].campo = partidaId`) → clave igual a `fotos/{obraId}/{id}.jpg` (`422 CLAVE_ARCHIVO_INVALIDA`, `errores[].campo = claveArchivo`).
- Si el `id` ya existe en **otra** obra: `422 CLAVE_ARCHIVO_INVALIDA` (la clave no puede corresponder a ese registro).

### GET /obras/{obraId}/fotos?partidaId=&desde=&hasta= · 005, 006, 007, 008
Roles: los que pueden ver la obra; `cliente` solo recibe publicadas. Paginado (convenciones): `pagina`, `tamano`, `orden`, `direccion`; responde `{ items, total, pagina, tamano }` con orden por defecto `registradoEn desc`. Cada elemento:
`[{ "id", "partidaId", "registradoPor", "registradoEn", "publicadaCliente", "urlDescarga" }]`
- `urlDescarga`: URL firmada de S3 para `GET`, vence a los 30 minutos.
- `cliente`: con `azp = portal` solo recibe las que tienen `publicada_cliente = true` (un usuario que además ve la obra con un rol interno las recibe todas).
- Paginación: `pagina` desde 1 (por defecto 1), `tamano` de 1 a 100 (por defecto 20), `orden` solo `registradoEn` (desempata por `id`), `direccion` `asc` o `desc` (por defecto `desc`). Fuera de eso: `400 PAGINACION_INVALIDA` con `errores[].campo` (`pagina`, `tamano`, `orden` o `direccion`). Un `partidaId` que no es UUID o un número mal escrito: `400 DATOS_INVALIDOS`.
- Errores: `404 OBRA_NO_ENCONTRADA` (no existe, no la ve o el id no es un UUID) · `503 SERVICIO_NO_DISPONIBLE` si hay fotos que firmar y S3 no está configurado (una página vacía responde igual).
- `partidaId` filtra; `desde` y `hasta` se aceptan pero no filtran (ninguna funcionalidad del sistema base los usa). La galería del periodo de la 007 pide páginas en orden `registradoEn desc` hasta pasar del periodo y filtra en el ERP; el periodo de las fotos adjuntas lo valida `PUT /planillas/{id}` (`422 FOTO_FUERA_DEL_PERIODO`).

### POST /obras/{obraId}/planillas · 007
Roles: `residente` asignado. Recibe `{ "periodo": "2027-03" }`. Devuelve `201` con el detalle (`GET /planillas/{id}`).
Errores: `409 PLANILLA_ABIERTA_EXISTE` · `422 PERIODO_NO_POSTERIOR` (no es posterior al de la última planilla) · `422 PERIODO_FUTURO` (mes futuro) · `422 OBRA_NO_EN_EJECUCION`.
- Orden: rol (`403 SIN_PERMISO`, antes que el cuerpo) → `periodo` (`400 DATOS_INVALIDOS` si falta o no es `AAAA-MM`; `errores[].campo = periodo`) → obra visible (`404 OBRA_NO_ENCONTRADA`, también si el id no es un UUID) → residente de esa obra (`403 SIN_PERMISO` si la ve con otro papel) → `OBRA_NO_EN_EJECUCION` → `PLANILLA_ABIERTA_EXISTE` (hay una planilla que no está `APROBADA`) → `PERIODO_NO_POSTERIOR` (`errores[].campo = periodo`) → `PERIODO_FUTURO` (posterior al mes de hoy en `America/La_Paz`).
- `numero` = el mayor de la obra + 1 (empieza en 1); una línea por partida en 0 y totales en 0; `version: 0`. Devuelve `Location: /planillas/{id}`. No publica eventos (la 010 agregará `PlanillaPresentada` al presentar).

### GET /obras/{obraId}/planillas · 006, 007, 008
Roles: los que pueden ver la obra; `cliente` no recibe `BORRADOR`.
`[{ "id", "numero", "periodo", "estado", "neto", "cobroId", "cobroNumero" }]` (`cobroNumero`: número del cobro, `COB-AAAA-NNNN`, que Obras guarda al recibir `CobroEmitido`; `null` hasta que se aprueba)
- Ordenadas por `numero` ascendente. El `cliente` que ve la obra solo como cliente (desde el portal) no recibe las `BORRADOR`. Errores: `404 OBRA_NO_ENCONTRADA` (no existe, no la ve o el id no es un UUID).

### GET /planillas/{id} · 007, 008
Roles: los que pueden ver la obra; `cliente` recibe `404` si es `BORRADOR`.
```json
{
  "id": "uuid", "obraId": "uuid", "numero": 3, "periodo": "2027-03", "estado": "BORRADOR", "version": 2,
  "lineas": [{
    "partidaId": "uuid", "codigo": "P-01", "nombre": "…", "unidad": "m2", "precioUnitario": 420.50,
    "cantidadContratada": 850, "acumuladoAnterior": 300, "cantidadPeriodo": 120,
    "acumuladoTotal": 420, "saldo": 430, "monto": 50460.00
  }],
  "subtotal": 50460.00, "descuentoAnticipo": 5046.00, "retencion": 3532.20, "neto": 41881.80,
  "anticipoPct": 10, "retencionPct": 7,
  "observacion": null, "cobroId": null, "cobroNumero": null, "fotoIds": ["uuid"]
}
```
- Errores: `404 PLANILLA_NO_ENCONTRADA` (no existe, no ve su obra, el id no es un UUID o es `BORRADOR` y la ve solo como cliente).
- Líneas por `codigo` de partida. `acumuladoAnterior` = suma de `cantidad_periodo` de la partida en las planillas `APROBADA` de la obra con periodo anterior; `acumuladoTotal` = anterior + periodo; `saldo` = contratada − acumulado total. Cantidades con 3 decimales, montos con 2. `anticipoPct` y `retencionPct` son los de la copia del contrato de la obra. `fotoIds` ordenados por id.

### PUT /planillas/{id} · 007
Roles: `residente` asignado. Recibe `{ "version": 2, "lineas": [{ "partidaId": "uuid", "cantidadPeriodo": 120 }], "fotoIds": ["uuid"] }`.
Devuelve `200` con el detalle. Errores: `409 VERSION_DESACTUALIZADA` · `409 PLANILLA_NO_EDITABLE` · `422 SUPERA_CANTIDAD_CONTRATADA` (con `errores[]`, `campo` = `partidaId`) · `422 FOTO_FUERA_DEL_PERIODO`.
- Orden: rol (`403`, antes que el cuerpo) → cuerpo (`400 DATOS_INVALIDOS`: `version`, `lineas` y `fotoIds` obligatorios; cada línea con `partidaId` y `cantidadPeriodo` ≥ 0 con hasta 3 decimales; una partida que no es de la planilla o repetida: `errores[].campo = lineas[i].partidaId`) → planilla visible (`404 PLANILLA_NO_ENCONTRADA`) → residente de esa obra (`403 SIN_PERMISO`) → `PLANILLA_NO_EDITABLE` (no está en `BORRADOR` ni `OBSERVADA`; `detail`: «La planilla ya no se puede editar (estado X)») → `VERSION_DESACTUALIZADA` (la `version` no es la guardada, o otra petición la guardó a la vez) → `SUPERA_CANTIDAD_CONTRATADA` → `FOTO_FUERA_DEL_PERIODO`.
- Las partidas que no vienen en `lineas` quedan como estaban. Cada guardado sube `version` en 1.
- `SUPERA_CANTIDAD_CONTRATADA`: un elemento de `errores[]` por partida que se pasa, con `campo` = el id de la partida y `mensaje` = «Supera lo contratado (saldo: X)» (X = contratada − acumulado anterior).
- `fotoIds` reemplaza las fotos adjuntas (los repetidos cuentan una vez). `FOTO_FUERA_DEL_PERIODO`: una foto que no existe, no es de la obra o no se registró dentro del mes del periodo (`registradoEn` en `America/La_Paz`); un elemento de `errores[]` por foto, con `campo` = el id de la foto. Si algo falla no se guarda nada.

### POST /planillas/{id}/presentar · 007
Roles: `residente` asignado. Recibe `{ "version": 3 }`. Devuelve `200` con el detalle (`PRESENTADA`).
Errores: `409 VERSION_DESACTUALIZADA` · `409 PLANILLA_NO_EDITABLE` · `422 SUBTOTAL_CERO`.
- Orden como en `PUT /planillas/{id}`: rol → cuerpo (`version` obligatoria) → planilla visible → residente → `PLANILLA_NO_EDITABLE` → `VERSION_DESACTUALIZADA` → `SUBTOTAL_CERO`. Se presenta desde `BORRADOR` u `OBSERVADA` (corregida; la `observacion` se conserva). Pasa a `PRESENTADA`, sube `version` y marca `publicada_cliente = true` en las fotos adjuntas. Todavía no publica `PlanillaPresentada` (010).

### POST /planillas/{id}/aprobar · 008
Roles: `cliente` dueño de la obra. Sin cuerpo. Devuelve `202 { "estado": "APROBANDO" }`.
Errores: `409 PLANILLA_NO_PRESENTADA`.
- Orden: rol (`403 SIN_PERMISO`; con `azp` distinto de `portal` el rol `cliente` no cuenta) → planilla visible (`404 PLANILLA_NO_ENCONTRADA`: no existe, no ve su obra, el id no es un UUID o es un `BORRADOR` que ve solo como cliente) → cliente de esa obra (`403 SIN_PERMISO` si la ve con otro papel) → `PLANILLA_NO_PRESENTADA` (no está `PRESENTADA`, o otra petición la revisó a la vez).
- En la misma transacción: la planilla pasa a `APROBANDO` (sube `version`), se crea la saga (`saga_aprobacion` `EN_CURSO`, paso `RESERVAR_SALDO`, vence a los 60 s) y se deja `comando.finanzas.ReservarSaldo` en el outbox con `monto` = neto y `correlacionId` = id de la saga. El resultado se consulta con `GET /planillas/{id}`.

### POST /planillas/{id}/observar · 008
Roles: `cliente` dueño de la obra. Recibe `{ "motivo": "texto de 10 a 500 caracteres" }`. Devuelve `200` con el detalle (`OBSERVADA`).
Errores: `409 PLANILLA_NO_PRESENTADA` · `422 MOTIVO_INVALIDO`.
- Orden: rol (`403`, antes que el cuerpo) → cuerpo JSON (`400 DATOS_INVALIDOS` si no es JSON) → planilla visible (`404`) → cliente de esa obra (`403`) → `PLANILLA_NO_PRESENTADA` → `MOTIVO_INVALIDO` (falta, o tiene menos de 10 o más de 500 caracteres contados sin los espacios de los extremos; `errores[].campo = motivo`).
- Guarda el motivo sin los espacios de los extremos en `observacion`, sube `version` y publica `PlanillaObservada` con `origen = "CLIENTE"` y `correlacionId` = id de la planilla. El residente la corrige y la vuelve a presentar (007).

## Eventos y comandos (RabbitMQ)

Sobre, exchange y claves según convenciones.

| Clave | Dirección | `datos` |
|---|---|---|
| `evento.obras.ObraRegistrada` | publica | `obraId, nombre, fechaInicio, asignaciones{residenteId, maestroId, almaceneroId, clienteId}, contrato{monto, plazoDias, anticipoPct, retencionPct, firmadoEn}` |
| `evento.finanzas.ContratoRegistrado` | escucha | `obraId, contratoId` |
| `evento.finanzas.ContratoRechazado` | escucha | `obraId, motivo` |
| `comando.finanzas.ReservarSaldo` | publica | `planillaId, obraId, monto` |
| `evento.finanzas.SaldoReservado` | escucha | `planillaId, reservaId` |
| `evento.finanzas.SaldoInsuficiente` | escucha | `planillaId, motivo` |
| `comando.materiales.CerrarConsumos` | publica | `planillaId, obraId, periodo` |
| `evento.materiales.ConsumosCerrados` | escucha | `planillaId, obraId, periodo` |
| `evento.materiales.CierreFallido` | escucha | `planillaId, motivo` |
| `comando.finanzas.EmitirCobro` | publica | `planillaId, reservaId, monto` |
| `evento.finanzas.CobroEmitido` | escucha | `planillaId, cobroId, numero, venceEn` (guarda `cobro_id` y `cobro_numero` en la planilla) |
| `evento.finanzas.CobroFallido` | escucha | `planillaId, motivo` |
| `comando.finanzas.LiberarReserva` | publica | `planillaId, reservaId` |
| `comando.materiales.ReabrirConsumos` | publica | `planillaId, obraId, periodo` |
| `evento.obras.PlanillaAprobada` | publica | `planillaId, obraId, periodo, neto, cobroId, lineas[{partidaId, cantidadPeriodo, monto}]` |
| `evento.obras.PlanillaObservada` | publica | `planillaId, obraId, motivo, origen ("CLIENTE" o "SAGA")` |

### Cómo publica y consume (002)

- **Publica** con outbox: el sobre completo se guarda en `outbox_evento` en la misma transacción que el dato y `PublicadorOutbox` lo envía cada 1 s al exchange `plataforma` con la clave `evento.obras.<Tipo>`, `contentType: application/json`, entrega persistente y `messageId` = `id` del sobre. Espera la confirmación de RabbitMQ antes de marcarlo publicado (si no llega, lo reintenta en la vuelta siguiente; los consumidores descartan repetidos por `id`). `correlacionId` de `ObraRegistrada` = `obraId`.
- **Consume** la cola `obras`. Cada mensaje se procesa una sola vez: su `id` se guarda en `mensaje_procesado` en la misma transacción que su efecto; si ya estaba, se confirma sin hacer nada. Si la obra ya no está en `SIN_CONTRATO`, `ContratoRegistrado` y `ContratoRechazado` no la cambian.
- **Mensajes que fallan.** El reintento y el envío a la cola `plataforma.fallidos` se publican por el exchange por defecto, con el cuerpo original sin tocar, y después se confirma el original:
  - si procesar falla, vuelve a la cola `obras` con `x-intentos` + 1; al tercer fallo va a `plataforma.fallidos`;
  - un sobre que no es JSON, con `version` distinta de 1, con datos incompletos o con un `tipo` sin consumidor en la cola `obras` va directo a `plataforma.fallidos`.

| Encabezado | Valor |
|---|---|
| `x-intentos` | Número de intentos ya hechos (1 en el primer fallo; 3 cuando llega a fallidos por reintentos) |
| `x-error` | Clase y texto del error, recortado a 500 caracteres; sin el cuerpo del mensaje ni datos sensibles |
| `x-cola-origen` | Cola de donde salió: `obras` |
| `x-clave-origen` | Clave de enrutamiento original (por ejemplo `evento.finanzas.ContratoRegistrado`); se conserva entre reintentos porque el exchange por defecto la pierde |

### Cómo orquesta la saga «aprobar planilla» (008)

- **Comandos** con la clave `comando.<destino>.<Tipo>` (guardada en `outbox_evento.clave`), por el mismo outbox. `correlacionId` = id de la saga en todos los comandos y en `PlanillaAprobada` / `PlanillaObservada` de la saga; `PlanillaObservada` del cliente lleva el id de la planilla. Montos con 2 decimales (`monto` = neto de la planilla) y `periodo` `AAAA-MM`.
- **Respuestas**: se busca la saga por el `correlacionId` del sobre (bloqueada con `SELECT … FOR UPDATE`) y se comprueba `datos.planillaId`. Sin `correlacionId`, con una saga que no existe, de otra planilla o con datos incompletos (`reservaId`; `cobroId` y `numero`; `motivo` de `CierreFallido` y `CobroFallido`), van directo a `plataforma.fallidos`. El `motivo` de `SaldoInsuficiente` no se usa: la planilla queda con «Saldo del contrato insuficiente». `venceEn` de `CobroEmitido` no se guarda (lo tiene Finanzas).
- Con la saga `EN_CURSO` y la respuesta del paso que espera: `SaldoReservado` → guarda `reservaId` y envía `CerrarConsumos`; `ConsumosCerrados` → envía `EmitirCobro` con la reserva; `CobroEmitido` → planilla `APROBADA` con `cobro_id` y `cobro_numero` (= `numero`), saga `COMPLETADA` y `PlanillaAprobada`. Cada paso nuevo vence a los 60 s.
- Fallo (`SaldoInsuficiente`, `CierreFallido`, `CobroFallido`): compensaciones de los pasos hechos en orden inverso, sin esperar respuesta (fallo en el paso 3: `LiberarReserva`; en el 4: `ReabrirConsumos` y `LiberarReserva`); planilla `OBSERVADA` con el motivo; saga `COMPENSADA`; `PlanillaObservada` con `origen = "SAGA"`.
- Una respuesta de otro paso o con la saga ya terminada no cambia nada (queda en el registro); los repetidos con el mismo `id` los descarta `mensaje_procesado`.

## Herramientas MCP (capa 5, solo lectura)

`avance_obra`, `buscar_diario`, `planillas_estado`, `asistencia_obra` (si existe asistencia). Se especifican en la capa 5.
