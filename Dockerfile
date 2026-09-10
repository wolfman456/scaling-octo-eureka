FROM node:24-alpine AS frontend
ARG FRONTEND_REPO=https://github.com/wolfman456/refactored-couscous.git
ARG FRONTEND_BRANCH=master
RUN apk add --no-cache git
RUN git clone --depth 1 --branch "$FRONTEND_BRANCH" "$FRONTEND_REPO" /frontend
WORKDIR /frontend
RUN npm ci && npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY . .
COPY --from=frontend /frontend/dist src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
