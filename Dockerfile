# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first to cache dependencies
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application and ensure executable Boot jar
# Explicitly run spring-boot:repackage to add Main-Class manifest
RUN mvn -B -DskipTests clean package spring-boot:repackage

# Production stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from build stage (single boot jar)
COPY --from=build /app/target/*.jar app.jar

# Copy environment variables
COPY .env .env

# Expose port 8093
EXPOSE 8093

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
