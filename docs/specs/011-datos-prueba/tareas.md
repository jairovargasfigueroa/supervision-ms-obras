# 011 · Datos de prueba · tareas de `services/obras`

Requisitos y diseño: `docs/specs/011-datos-prueba/` de la carpeta que agrupa los proyectos. Datos: `docs/datos-prueba.md` (secciones 6, 7 y 10). Contratos: `docs/contratos.md` de este proyecto (no cambian).

Se implementa **después de la 009**, cuando existen todas las tablas del sistema base. Si una capa agrega tablas o campos a Obras (diario 103, zona 401, campos de la capa 3), esa capa amplía esta semilla en el mismo trabajo.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Resolvedor de fechas relativas (`D±n`, `M-k`, `M-k/dd hh:mm`, `D-n hh:mm`) en zona `America/La_Paz` con D desde `semilla.fecha` (`SEMILLA_FECHA`) o la fecha de hoy; pruebas unitarias con la tabla del diseño
- [x] `src/main/resources/semilla/obras.json` con la parte de Obras de `docs/datos-prueba.md`: 3 obras con copia del contrato y `contrato_id`, 21 partidas con insumos, 5 planillas con sus cantidades, 10 fotos y las fotos adjuntas
- [x] Perfil `semilla` (`application-semilla.yaml`: sin servidor web, sin consumidores ni publicador del outbox) y `SemillaRunner`: carga en una transacción con `INSERT … ON CONFLICT DO NOTHING`, calcula montos y totales con la regla del dominio, registra el resumen y termina el proceso; guarda contra perfil `prod` y base no local
- [x] Modo `semilla.modo=recargar`: vacía todas las tablas del negocio (no `flyway_schema_history`) y carga
- [x] Pruebas IT (Testcontainers, D = 2026-10-28): dos cargas sin duplicar; indicadores de las 3 obras; totales de las 5 planillas; material previsto de la obra 1 en `2026-09`; visibilidad por usuario; `outbox_evento` vacía; guarda
- [x] README: cómo correr la semilla sola y con `recargar`
