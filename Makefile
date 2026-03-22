# NexaTech Backend

## Build
.PHONY: build clean compile infra-up infra-down up down logs rebuild reset

build:
	mvn clean package -DskipTests

clean:
	mvn clean

compile:
	mvn clean compile

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

## Docker - Dev environment (docker-compose-dev.yml)
dev-up:
	docker-compose -f docker-compose-dev.yml up -d

dev-down:
	docker-compose -f docker-compose-dev.yml down

dev-rebuild:
	docker-compose -f docker-compose-dev.yml up -d --build

dev-logs:
	docker-compose -f docker-compose-dev.yml logs -f

dev-reset:
	docker-compose -f docker-compose-dev.yml down -v
	docker-compose -f docker-compose-dev.yml up -d --build
