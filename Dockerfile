# syntax=docker/dockerfile:1
# Imagen de Obras (601, docs/specs/601-contenedores-ci/ de supervision-docs). Dos etapas: «construir» compila con el JDK
# (sin pruebas: corren antes, en el workflow) y «final» solo trae el JRE y el .jar. Sin .env.properties: toda la
# configuración entra por las variables OBRAS_* al arrancar. Imágenes base revisadas en Docker Hub el 03/11/2026
# (eclipse-temurin 21.0.12_8, publicada el 18/09/2026).

FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.24 AS construir
WORKDIR /fuente
# Primero el wrapper y el pom: las dependencias quedan en su propia capa y se reusan mientras el pom no cambie
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN sh ./mvnw -B -q package -DskipTests && cp target/obras-*.jar /app.jar

FROM eclipse-temurin:21.0.12_8-jre-alpine-3.24 AS final
LABEL org.opencontainers.image.source="https://github.com/jairovargasfigueroa/supervision-ms-obras" \
      org.opencontainers.image.description="Servicio Obras de la plataforma de supervisión de obras"
# Usuario sin privilegios (convención de la 601: numérico, 10001)
RUN addgroup -S -g 10001 obras && adduser -S -D -H -u 10001 -G obras -h /app obras
WORKDIR /app
COPY --from=construir /app.jar app.jar
# Perfil prod (application-prod.yaml) y puerto de convenciones.md; los dos se pueden cambiar al arrancar
ENV SPRING_PROFILES_ACTIVE=prod \
    OBRAS_PORT=8081
EXPOSE 8081
USER 10001:10001
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${OBRAS_PORT}/salud" || exit 1
# Se conserva el ENTRYPOINT de la imagen base (agrega certificados con USE_SYSTEM_CA_CERTS si se pide)
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
