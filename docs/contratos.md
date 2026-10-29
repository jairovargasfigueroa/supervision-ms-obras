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
Errores: `404 OBRA_NO_ENCONTRADA` (también si el usuario no puede verla).

### GET /obras/{id}/material-previsto?periodo=AAAA-MM · 006
Roles: `admin`, `gerente` y `residente` asignado (si no ve la obra: `404 OBRA_NO_ENCONTRADA`).
`{ "periodo": "2027-02", "hayPlanilla": true, "items": [{ "partidaId": "…", "materialId": "…", "cantidadPrevista": 55.0 }] }`

### POST /obras/{obraId}/fotos/url-subida · 005
Roles: `maestro`, `residente` asignados. Recibe `{ "fotoId": "uuid" }`.
Devuelve `{ "url": "https://…", "claveArchivo": "fotos/{obraId}/{fotoId}.jpg", "expiraEn": "…" }`.

### POST /obras/{obraId}/fotos · 005
Roles: `maestro`, `residente` asignados.
Recibe `{ "id": "uuid", "partidaId": "uuid", "claveArchivo": "fotos/…jpg", "tomadaEn": "2026-11-15T15:20:00Z" }`.
Devuelve `201` (o `200` si el `id` ya existía) `{ "id", "partidaId", "registradoPor", "registradoEn", "publicadaCliente": false }`.
Errores: `404 OBRA_NO_ENCONTRADA` · `422 PARTIDA_NO_ES_DE_LA_OBRA` · `422 CLAVE_ARCHIVO_INVALIDA`.

### GET /obras/{obraId}/fotos?partidaId=&desde=&hasta= · 005, 006, 007, 008
Roles: los que pueden ver la obra; `cliente` solo recibe publicadas. Paginado (convenciones): `pagina`, `tamano`, `orden`, `direccion`; responde `{ items, total, pagina, tamano }` con orden por defecto `registradoEn desc`. Cada elemento:
`[{ "id", "partidaId", "registradoPor", "registradoEn", "publicadaCliente", "urlDescarga" }]`

### POST /obras/{obraId}/planillas · 007
Roles: `residente` asignado. Recibe `{ "periodo": "2027-03" }`. Devuelve `201` con el detalle (`GET /planillas/{id}`).
Errores: `409 PLANILLA_ABIERTA_EXISTE` · `422 PERIODO_NO_POSTERIOR` (no es posterior al de la última planilla) · `422 PERIODO_FUTURO` (mes futuro) · `422 OBRA_NO_EN_EJECUCION`.

### GET /obras/{obraId}/planillas · 006, 007, 008
Roles: los que pueden ver la obra; `cliente` no recibe `BORRADOR`.
`[{ "id", "numero", "periodo", "estado", "neto", "cobroId", "cobroNumero" }]` (`cobroNumero`: número del cobro, `COB-AAAA-NNNN`, que Obras guarda al recibir `CobroEmitido`; `null` hasta que se aprueba)

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

### PUT /planillas/{id} · 007
Roles: `residente` asignado. Recibe `{ "version": 2, "lineas": [{ "partidaId": "uuid", "cantidadPeriodo": 120 }], "fotoIds": ["uuid"] }`.
Devuelve `200` con el detalle. Errores: `409 VERSION_DESACTUALIZADA` · `409 PLANILLA_NO_EDITABLE` · `422 SUPERA_CANTIDAD_CONTRATADA` (con `errores[]`, `campo` = `partidaId`) · `422 FOTO_FUERA_DEL_PERIODO`.

### POST /planillas/{id}/presentar · 007
Roles: `residente` asignado. Recibe `{ "version": 3 }`. Devuelve `200` con el detalle (`PRESENTADA`).
Errores: `409 VERSION_DESACTUALIZADA` · `409 PLANILLA_NO_EDITABLE` · `422 SUBTOTAL_CERO`.

### POST /planillas/{id}/aprobar · 008
Roles: `cliente` dueño de la obra. Sin cuerpo. Devuelve `202 { "estado": "APROBANDO" }`.
Errores: `409 PLANILLA_NO_PRESENTADA`.

### POST /planillas/{id}/observar · 008
Roles: `cliente` dueño de la obra. Recibe `{ "motivo": "texto de 10 a 500 caracteres" }`. Devuelve `200` con el detalle (`OBSERVADA`).
Errores: `409 PLANILLA_NO_PRESENTADA` · `422 MOTIVO_INVALIDO`.

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

## Herramientas MCP (capa 5, solo lectura)

`avance_obra`, `buscar_diario`, `planillas_estado`, `asistencia_obra` (si existe asistencia). Se especifican en la capa 5.
