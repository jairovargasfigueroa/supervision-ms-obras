-- 301 · Detección de casco y chaleco en la foto de avance: el celular analiza la foto y envía los conteos con la
-- versión del modelo. personas_detectadas y sin_casco ya existen (V2). Los cuatro quedan en null cuando la foto no se
-- analizó o la detección se descartó por no ser válida. docs/datos.md

alter table foto_avance
    add column sin_chaleco        int,
    add column version_modelo_epp varchar(40);
