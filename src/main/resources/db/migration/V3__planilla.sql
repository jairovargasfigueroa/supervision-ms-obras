-- 006 · Consultar obra: tablas de la planilla de avance (docs/datos.md). La 006 solo las lee (avance de la obra,
-- ejecutada acumulada por partida, última planilla y material previsto del periodo); la 007 crea, guarda y presenta
-- planillas sobre estas mismas tablas (agrega planilla_foto en su propia migración).

create table planilla (
    id                 uuid primary key,
    obra_id            uuid           not null references obra (id),
    numero             int            not null,
    periodo            date           not null check (extract(day from periodo) = 1),
    estado             varchar(20)    not null
        check (estado in ('BORRADOR', 'PRESENTADA', 'APROBANDO', 'APROBADA', 'OBSERVADA')),
    subtotal           numeric(14, 2) not null,
    descuento_anticipo numeric(14, 2) not null,
    retencion          numeric(14, 2) not null,
    neto               numeric(14, 2) not null,
    observacion        text,
    cobro_id           uuid,
    cobro_numero       varchar(20),
    version            int            not null,
    constraint planilla_numero_unico unique (obra_id, numero),
    constraint planilla_periodo_unico unique (obra_id, periodo)
);

create table linea_planilla (
    planilla_id      uuid           not null references planilla (id),
    partida_id       uuid           not null references partida (id),
    cantidad_periodo numeric(14, 3) not null,
    monto            numeric(14, 2) not null,
    primary key (planilla_id, partida_id)
);
