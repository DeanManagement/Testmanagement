# Single image: the Angular app is built and baked into the Spring Boot jar, which serves both
# the SPA and the API on one port. Build context is the repository root.
#
# Replaces the previous pair of images. nginx is gone; its gzip, security headers and SPA
# fallback now live in the backend (ShellSecurityHeadersFilter, SpaResourceConfig,
# server.compression in application.yml).

# ── 1. Angular ──────────────────────────────────────────────────────────────────────────────
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build --configuration=production

# ── 2. Spring Boot, with the built SPA on its classpath ─────────────────────────────────────
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw dependency:resolve -B
COPY backend/src/ src/
# Spring serves classpath:/static/, so the SPA ships inside the jar.
COPY --from=frontend /app/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw package -DskipTests -B

# ── 3. Runtime ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
# curl is needed for the container healthcheck (PRD-019).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar

# Build-time version, surfaced at /actuator/info (PRD-023 releases).
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}

# PRD-019: run as non-root; the app listens on 8089 (see application.yml server.port).
USER app
EXPOSE 8089
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8089/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
