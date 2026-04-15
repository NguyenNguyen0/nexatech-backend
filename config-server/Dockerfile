# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY config-server/pom.xml config-server/pom.xml

RUN mvn dependency:go-offline -pl config-server -q

COPY config-server/src config-server/src

RUN mvn clean package -pl config-server -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/config-server/target/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
