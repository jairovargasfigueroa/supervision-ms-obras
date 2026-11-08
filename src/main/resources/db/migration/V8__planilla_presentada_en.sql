-- 501 · Servidores MCP (capa 5): última vez que la planilla pasó a PRESENTADA (007 al presentar y al volver a
-- presentar una observada). La usa planillas_estado para los días desde la presentación. Nula en las planillas
-- anteriores a esta migración. docs/datos.md

alter table planilla
    add column presentada_en timestamptz null;
