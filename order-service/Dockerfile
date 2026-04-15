# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy parent POM + this service POM only
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl order-service -q

# Copy source
COPY order-service/src order-service/src

# Build
RUN mvn clean package -pl order-service -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/order-service/target/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
