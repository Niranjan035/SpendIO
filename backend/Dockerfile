# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# SpendIO backend - multi-stage build (ARCHITECTURE.md Phase 0 / K.16)
#   build stage : Maven + JDK 21, tests skipped (they run in CI)
#   run  stage  : minimal JRE 21, non-root user, layered for cache reuse
# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Wrapper + descriptor first so dependency resolution is cached between builds.
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
    && cp target/spendio-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spendio \
    && useradd --system --gid spendio --create-home --home-dir /app \
         --shell /usr/sbin/nologin spendio

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
RUN chown -spendio:spendio /app/app.jar

USER spendio
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS=""

HEALTHCHECK --interval=15s --timeout=4s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
