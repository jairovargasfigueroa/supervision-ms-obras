-- 103 · Diario de obra (capa 1): una entrada por obra y por día, con el clima y el texto que anota el maestro. El id
-- lo crea el celular (idempotencia). registrado_en es el momento en que el servidor la recibió. docs/datos.md

create table entrada_diario (
    id             uuid primary key,
    obra_id        uuid        not null references obra (id),
    fecha          date        not null,
    clima          varchar(20) not null
        check (clima in ('SOLEADO', 'NUBLADO', 'LLUVIA_LEVE', 'LLUVIA_FUERTE')),
    texto          text        not null,
    registrado_por uuid        not null,
    registrado_en  timestamptz not null
);

-- Una sola entrada por obra y día: si dos llegan a la vez, la base rechaza la segunda (409 DIARIO_DIA_YA_REGISTRADO).
-- El mismo índice sirve a GET /obras/{obraId}/diario (por obra y rango de fechas).
create unique index entrada_diario_obra_fecha_unica on entrada_diario (obra_id, fecha);
