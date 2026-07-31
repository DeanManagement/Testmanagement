FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre
# curl is needed for the container healthcheck (PRD-019).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Build-time version, surfaced at /actuator/info (PRD-023 releases).
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}

# PRD-019: run as non-root; the app listens on 8089 (see application.yml server.port).
USER app
EXPOSE 8089
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8089/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
