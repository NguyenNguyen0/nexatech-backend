ARG SERVICE_NAME=discovery-server

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_NAME
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY ${SERVICE_NAME}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
