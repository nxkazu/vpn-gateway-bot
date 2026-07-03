# ---- Build stage -------------------------------------------------------
FROM gradle:8.11.1-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon shadowJar

# ---- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root runtime user
RUN groupadd --system tessera && useradd --system --gid tessera tessera

COPY --from=build /home/gradle/project/build/libs/tessera-vpn-bot.jar /app/app.jar

USER tessera

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
