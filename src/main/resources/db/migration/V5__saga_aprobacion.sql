-- 008 · Revisar planilla (saga «aprobar planilla»): estado de la saga que orquesta Obras (docs/datos.md). La columna
-- planilla.cobro_numero ya existe (V3). El outbox guarda además la clave de enrutamiento de cada mensaje, porque la
-- saga publica comandos (comando.finanzas.*, comando.materiales.*) y no solo eventos de Obras (evento.obras.*).

create table saga_aprobacion (
    id             uuid primary key,
    planilla_id    uuid        not null references planilla (id),
    paso_actual    varchar(20) not null
        check (paso_actual in ('RESERVAR_SALDO', 'CERRAR_CONSUMOS', 'EMITIR_COBRO')),
    estado         varchar(20) not null
        check (estado in ('EN_CURSO', 'COMPLETADA', 'COMPENSADA')),
    detalle        jsonb       not null default '{}'::jsonb,
    vence_en       timestamptz not null,
    actualizada_en timestamptz not null
);

-- Una planilla observada por la saga se puede corregir y aprobar otra vez (otra saga), pero nunca hay dos en curso
create unique index saga_aprobacion_en_curso_unica on saga_aprobacion (planilla_id) where estado = 'EN_CURSO';

-- La tarea de vencimiento (cada 10 s) busca las sagas en curso con el plazo del paso vencido
create index saga_aprobacion_vencimiento_idx on saga_aprobacion (vence_en) where estado = 'EN_CURSO';

alter table outbox_evento add column clave varchar(150);
update outbox_evento set clave = 'evento.obras.' || tipo;
alter table outbox_evento alter column clave set not null;
