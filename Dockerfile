# Multi-stage build: frontend → backend JAR
# Target: eclipse-temurin:21-jre, < 300MB

# Stage 1: Build frontend
FROM node:24-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# Stage 2: Build backend
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
# Copy backend source
COPY backend/pom.xml .
COPY backend/src ./src
# Copy frontend build output (vite outDir: ../backend/src/main/resources/static)
COPY --from=frontend-build /app/backend/src/main/resources/static ./src/main/resources/static
# Pre-fetch dependencies offline, then package
RUN mvn dependency:go-offline -q -B
RUN mvn package -DskipTests -q -B

# Stage 3: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
# Create directories for workspace data and master key
RUN mkdir -p /app/workspace /app/.tepeu && \
    chmod 755 /app/workspace /app/.tepeu

COPY --from=backend-build /app/target/tepeu.jar tepeu.jar

EXPOSE 30141

VOLUME ["/app/workspace", "/app/.tepeu"]

ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
ENV TEPEU_HOME=/app

CMD ["java", "-jar", "tepeu.jar"]
