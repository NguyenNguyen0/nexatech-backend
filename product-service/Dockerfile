FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl product-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/product-service/target/product-service-*.jar app.jar
USER spring
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
