-- 401 · Base de reportes (capa 4): zona de la obra, opcional (null = sin zona). Viaja en ObraRegistrada y se usa en
-- los reportes que cruzan servicios. docs/datos.md

alter table obra
    add column zona varchar(10) null
        check (zona in ('NORTE', 'SUR', 'ESTE', 'OESTE', 'CENTRO'));
