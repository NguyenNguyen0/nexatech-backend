# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY discovery-server/pom.xml discovery-server/pom.xml

RUN mvn dependency:go-offline -pl discovery-server -q

COPY discovery-server/src discovery-server/src

RUN mvn clean package -pl discovery-server -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/discovery-server/target/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
