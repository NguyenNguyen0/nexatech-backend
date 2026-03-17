# NexaTech Backend

## Build
.PHONY: build clean infra-up infra-down up down logs

build:
	cd nexatech-backend && mvn clean package -DskipTests

clean:
	cd nexatech-backend && mvn clean

compile:
	cd nexatech-backend && mvn clean compile

## Docker - Infrastructure only
infra-up:
	docker-compose up -d postgres-identity postgres-user postgres-order postgres-inventory postgres-payment postgres-notification mongodb redis zookeeper kafka jaeger

infra-down:
	docker-compose down

## Docker - All services
up:
	docker-compose up -d --build

down:
	docker-compose down

## Docker - Rebuild a single service (e.g. make rebuild SERVICE=identity-service)
rebuild:
	docker-compose up -d --build $(SERVICE)

## View logs (e.g. make logs SERVICE=identity-service)
logs:
	docker-compose logs -f $(SERVICE)

## Reset everything including volumes
reset:
	docker-compose down -v
	docker-compose up -d --build
