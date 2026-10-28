# 601 · Contenedores y CI/CD · tareas de `services/obras`

Requisitos y diseño: `docs/specs/601-contenedores-ci/` de la carpeta que agrupa los proyectos. Contratos: `docs/contratos.md` de este proyecto.

Marca cada tarea al terminarla (con sus pruebas y su commit).

- [ ] Variables propuestas en `application.yml` con valores de desarrollo por defecto: `OBRAS_DB_SSLMODE`, `OBRAS_S3_REGION`, `OBRAS_KEYCLOAK_ISSUER`, `OBRAS_RABBITMQ_HOST`, `OBRAS_RABBITMQ_PORT`, `OBRAS_RABBITMQ_USER`, `OBRAS_RABBITMQ_PASSWORD`, `OBRAS_RABBITMQ_SSL`, `OBRAS_CORS_ORIGENES` (las `OBRAS_DB_*` y `OBRAS_S3_*` ya existen); con `OBRAS_S3_ENDPOINT` vacío usa S3 real
- [ ] `Dockerfile` (JDK para compilar, `eclipse-temurin:21-jre-alpine` para correr, usuario sin privilegios, `HEALTHCHECK` a `/salud`) y `.dockerignore`
- [ ] `.github/workflows/ci-cd.yml`: `./mvnw -B verify`, imagen en GHCR con etiquetas `sha-<7>` y `main`, despliegue con `az containerapp update` por OIDC
- [ ] Comprobar: imagen sin `.env.properties`, `id -u` distinto de 0, `/salud` responde dentro del contenedor
