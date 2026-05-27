ARG SERVICE_NAME

FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG SERVICE_NAME
WORKDIR /workspace
COPY . .
RUN mvn -pl "${SERVICE_NAME}" -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_NAME
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar app.jar
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
