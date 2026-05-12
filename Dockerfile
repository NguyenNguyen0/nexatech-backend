ARG SERVICE_NAME

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
ARG SERVICE_NAME
WORKDIR /build

# Copy parent POM
COPY pom.xml .

# Copy all module POMs để cache dependency resolution
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
COPY chat-service/pom.xml chat-service/pom.xml
COPY storage-service/pom.xml storage-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl ${SERVICE_NAME} -am -q

# Copy source code of the target service and its dependencies
COPY api-gateway/src api-gateway/src
COPY discovery-server/src discovery-server/src
COPY config-server/src config-server/src
COPY identity-service/src identity-service/src
COPY user-service/src user-service/src
COPY product-service/src product-service/src
COPY order-service/src order-service/src
COPY inventory-service/src inventory-service/src
COPY payment-service/src payment-service/src
COPY notification-service/src notification-service/src
COPY chat-service/src chat-service/src
COPY storage-service/src storage-service/src

# Build only the target service
RUN mvn clean package -pl ${SERVICE_NAME} -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE_NAME
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/${SERVICE_NAME}/target/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

ENTRYPOINT ["java", "-jar", "app.jar"]
