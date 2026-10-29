# 001 · Iniciar sesión y paso entre aplicaciones · tareas de `services/obras`

Requisitos y diseño: `docs/specs/001-iniciar-sesion/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [x] Convertidor de `realm_access.roles` a autoridades `ROLE_<rol>` y `@EnableMethodSecurity`
- [x] Perfil de pruebas con clave de firma local y utilidad para generar tokens por rol
- [ ] Pruebas 401/403/200 sobre un endpoint de ejemplo protegido
