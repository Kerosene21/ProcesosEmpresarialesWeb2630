# syntax=docker/dockerfile:1.7
# Imagen del backend Spring Boot. Contexto de construccion: la raiz del repositorio.
#   docker build -t procesos-empresariales-backend:local .
# Las pruebas que necesitan PostgreSQL (*IntegracionTest) se ejecutan con ./mvnw clean verify fuera
# de la imagen; aqui se ejecutan las unitarias y las de controladores y seguridad.

FROM eclipse-temurin:21-jdk-alpine@sha256:82801f9dcdcbad5a05082aba8c86d1da5b72ccb498e71318b1f91b5a477887b3 AS compilacion
WORKDIR /fuente
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package \
        '-Dtest=!*IntegracionTest*,!ProcesosEmpresarialesWeb2630ApplicationTests*' \
        -Dsurefire.failIfNoSpecifiedTests=false
RUN cp target/procesos-empresariales-web2630-*.jar aplicacion.jar \
    && java -Djarmode=tools -jar aplicacion.jar extract --layers --destination extraido

FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474 AS ejecucion
LABEL org.opencontainers.image.title="procesos-empresariales-backend" \
      org.opencontainers.image.description="API REST y vistas Thymeleaf de Procesos Empresariales"
RUN addgroup -S -g 10001 aplicacion && adduser -S -D -H -u 10001 -G aplicacion aplicacion
WORKDIR /app
COPY --from=compilacion /fuente/extraido/dependencies/ ./
COPY --from=compilacion /fuente/extraido/spring-boot-loader/ ./
COPY --from=compilacion /fuente/extraido/snapshot-dependencies/ ./
COPY --from=compilacion /fuente/extraido/application/ ./
ENV SERVER_PORT=8080 \
    SPRING_JPA_SHOW_SQL=false \
    SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "aplicacion.jar"]
