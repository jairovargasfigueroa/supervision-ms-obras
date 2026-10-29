-- 005 · Registrar foto de avance: la foto se guarda en S3 (clave_archivo) y aquí su registro. El id lo crea el
-- celular (idempotencia). Las columnas de la capa 3 (clave_archivo_cliente, personas_detectadas, sin_casco) quedan
-- en null hasta la 301 y la 303. docs/datos.md

create table foto_avance (
    id                    uuid primary key,
    obra_id               uuid         not null references obra (id),
    partida_id            uuid         not null references partida (id),
    clave_archivo         varchar(200) not null,
    clave_archivo_cliente varchar(200),
    personas_detectadas   int,
    sin_casco             int,
    publicada_cliente     boolean      not null default false,
    registrado_por        uuid         not null,
    registrado_en         timestamptz  not null
);

-- GET /obras/{obraId}/fotos: por obra (y partida), de la más reciente a la más antigua
create index foto_avance_obra_idx on foto_avance (obra_id, registrado_en desc);
create index foto_avance_partida_idx on foto_avance (partida_id, registrado_en desc);
