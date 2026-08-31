FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.15-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 taskapp && mkdir -p /var/lib/taskapp/uploads \
    && chown -R taskapp:taskapp /app /var/lib/taskapp
COPY --from=backend-build /workspace/target/task-app-*.jar /app/task-app.jar
USER taskapp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/task-app.jar"]
