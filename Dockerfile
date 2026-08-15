# ---- Build stage -------------------------------------------------------
FROM gradle:9.7.0-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN ./gradlew --no-daemon :tessera-app:shadowJar :tessera-payment:shadowJar :tessera-dashboard:shadowJar

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Non-root runtime user
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system tessera \
    && useradd --system --gid tessera tessera

COPY --from=build /home/gradle/project/tessera-app/build/libs/tessera-vpn-bot.jar /app/app.jar
# Pluggable modules: every jar in /app/modules is loaded at startup.
COPY --from=build /home/gradle/project/tessera-payment/build/libs/tessera-payment.jar /app/modules/tessera-payment.jar
COPY --from=build /home/gradle/project/tessera-dashboard/build/libs/tessera-dashboard.jar /app/modules/tessera-dashboard.jar

RUN mkdir -p /app/config /app/backups && chown -R tessera:tessera /app

USER tessera

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    MODULES_DIR=/app/modules \
    MODULES_CONFIG_DIR=/app/config \
    DASHBOARD_BIND=0.0.0.0 \
    WEBAPP_BIND=0.0.0.0 \
    OPS_BIND=0.0.0.0 \
    BACKUP_DIR=/app/backups

EXPOSE 8080 8090 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
