-- 002 · Registrar obra: obra con la copia del contrato, presupuesto por partidas, material previsto por unidad,
-- y las tablas comunes de mensajería (outbox para publicar, inbox para no procesar dos veces). docs/datos.md

create table obra (
    id                      uuid primary key,
    nombre                  varchar(150)   not null,
    ubicacion               varchar(200)   not null,
    perimetro               jsonb,
    estado                  varchar(20)    not null
        check (estado in ('SIN_CONTRATO', 'EN_EJECUCION', 'CONCLUIDA')),
    cliente_usuario_id      uuid           not null,
    residente_usuario_id    uuid           not null,
    maestro_usuario_id      uuid           not null,
    almacenero_usuario_id   uuid           not null,
    contrato_id             uuid,
    monto_contrato          numeric(14, 2) not null,
    plazo_dias              int            not null,
    anticipo_pct            numeric(5, 2)  not null,
    retencion_pct           numeric(5, 2)  not null,
    fecha_inicio            date           not null,
    motivo_rechazo_contrato text
);

-- Visibilidad por rol: cada usuario ve las obras donde está asignado (convenciones)
create index obra_cliente_idx on obra (cliente_usuario_id);
create index obra_residente_idx on obra (residente_usuario_id);
create index obra_maestro_idx on obra (maestro_usuario_id);
create index obra_almacenero_idx on obra (almacenero_usuario_id);

create table partida (
    id                  uuid primary key,
    obra_id             uuid           not null references obra (id),
    codigo              varchar        not null,
    nombre              varchar        not null,
    unidad              varchar        not null,
    cantidad_contratada numeric(14, 3) not null,
    precio_unitario     numeric(14, 2) not null,
    meses               varchar,
    constraint partida_codigo_unico unique (obra_id, codigo)
);

create table insumo_previsto (
    partida_id          uuid           not null references partida (id),
    material_id         varchar        not null,
    cantidad_por_unidad numeric(14, 3) not null,
    primary key (partida_id, material_id)
);

-- Outbox: el mensaje (sobre completo) se guarda en la misma transacción que el dato; PublicadorOutbox lo envía
create table outbox_evento (
    id           uuid primary key,
    tipo         varchar     not null,
    payload      jsonb       not null,
    creado_en    timestamptz not null,
    publicado_en timestamptz
);

create index outbox_evento_pendiente_idx on outbox_evento (creado_en) where publicado_en is null;

-- Inbox: id de cada mensaje ya procesado (se registra en la misma transacción que su efecto)
create table mensaje_procesado (
    id_mensaje   uuid primary key,
    procesado_en timestamptz not null
);
