-- 007 · Elaborar y presentar planilla: fotos de avance adjuntas como respaldo de la planilla (docs/datos.md). Al
-- presentar la planilla, sus fotos pasan a foto_avance.publicada_cliente = true. planilla y linea_planilla ya existen
-- (V3, de la 006).

create table planilla_foto (
    planilla_id uuid not null references planilla (id),
    foto_id     uuid not null references foto_avance (id),
    primary key (planilla_id, foto_id)
);
