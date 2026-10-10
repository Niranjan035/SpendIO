# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# SpendIO backend - multi-stage, layered build (ARCHITECTURE.md Phase 0 / K.16).
#   build stage : Maven + JDK 21, tests skipped (they run in CI)
#   run  stage  : minimal JRE 21, non-root user, Spring Boot layers copied in
#                 dependency order so a code-only change rebuilds one layer.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Wrapper + descriptor first so dependency resolution is cached between builds.
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src/ src/
# Spring Boot 3.4 `jarmode=tools` layer extraction emits a thin, runnable jar in
# `application/` (its manifest Main-Class is com.spendio.SpendioApplication and
# Class-Path points at lib/) and does NOT emit a dedicated loader layer. The
# extracted jar is named after the input jar, so building to app.jar yields a
# stable `/app/app.jar` for the runtime entrypoint.
RUN ./mvnw -B -DskipTests package \
    && cp target/spendio-backend-*.jar /workspace/app.jar \
    && java -Djarmode=tools -jar /workspace/app.jar extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: targeted patched packages from jammy-security/jammy-updates (no full upgrade,
# base OS unchanged). Base image otherwise ships the older versions:
#   libssl3       3.0.2-0ubuntu1.29 -> 3.0.2-0ubuntu1.30 (CVE-2026-84782)
#   libfreetype6  2.11.1+dfsg-1ubuntu0.3 -> 2.11.1+dfsg-1ubuntu0.4 (CVE-2026-95512)
#   libpng16-16   1.6.37-3ubuntu0.6 -> 1.6.37-3ubuntu0.7 (CVE-2026-46675)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get install -y --only-upgrade libssl3 libfreetype6 libpng16-16 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spendio \
    && useradd --system --gid spendio --create-home --home-dir /app \
         --shell /usr/sbin/nologin spendio

WORKDIR /app
# Least-volatile layer first: dependencies rarely change, application code changes often.
COPY --from=build /workspace/extracted/dependencies/ ./
# Empty with the Spring Boot 3.4 tools layout; copied for compatibility with
# older/future layouts that do populate a loader layer.
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
# Thin runnable jar; its manifest Class-Path resolves lib/ alongside it in /app.
COPY --from=build /workspace/extracted/application/ ./
RUN chown -R spendio:spendio /app

USER spendio
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS=""

HEALTHCHECK --interval=15s --timeout=4s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Launch the extracted thin jar directly. The Spring Boot 3.4 tools layout has no
# spring-boot-loader layer, so org.springframework.boot.loader.launch.JarLauncher
# is not on the classpath; `-jar` uses the jar's manifest Main-Class + Class-Path.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
