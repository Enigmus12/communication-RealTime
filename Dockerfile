# ===== Stage 1: build =====
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copiamos POM y descargamos dependencias (cacheable)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -DskipTests dependency:go-offline

# Copiamos el código y compilamos
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -DskipTests package

# ===== Stage 2: runtime =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el JAR construido (nombre comodín)
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8093
ENTRYPOINT ["java","-jar","/app/app.jar"]
