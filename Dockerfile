# ---- Build stage -------------------------------------------------------
FROM gradle:9.6.1-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN ./gradlew --no-daemon :app:shadowJar :payment-module:shadowJar :admin-dashboard:shadowJar

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root runtime user
RUN groupadd --system tessera && useradd --system --gid tessera tessera

COPY --from=build /home/gradle/project/app/build/libs/tessera-vpn-bot.jar /app/app.jar
# Pluggable modules: every jar in /app/modules is loaded at startup.
COPY --from=build /home/gradle/project/payment-module/build/libs/PaymentModule.jar /app/modules/PaymentModule.jar
COPY --from=build /home/gradle/project/admin-dashboard/build/libs/AdminDashboard.jar /app/modules/AdminDashboard.jar

RUN mkdir -p /app/config && chown -R tessera:tessera /app

USER tessera

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    MODULES_DIR=/app/modules \
    MODULES_CONFIG_DIR=/app/config \
    DASHBOARD_BIND=0.0.0.0

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
