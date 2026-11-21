FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY scripts/deploy-check.sh /app/deploy-check.sh
ENV SPRING_PROFILES_ACTIVE=production
ENV APP_UPLOAD_DIR=/app/data/uploads
ENV APP_ORIGINALS_DIR=/app/data/originals
EXPOSE 8080
ENTRYPOINT ["/bin/bash", "-c", "bash /app/deploy-check.sh && exec java -jar app.jar"]
