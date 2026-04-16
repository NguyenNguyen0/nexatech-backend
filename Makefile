# ================================================================================================
# NEXATECH BACKEND - PROJECT MANAGEMENT (Windows/PowerShell Compatible)
# ================================================================================================

# Biến mặc định
DOCKER_INFRA = docker-compose.infra.yml
DOCKER_FULL = docker-compose.yml
MVN = mvn

.PHONY: help build clean compile dev-up dev-down full-up full-down infra-reset logs-infra logs-full prune \
	run-discovery run-config run-gateway \
	run-identity run-user run-product run-inventory run-order run-payment run-notification run-storage

help:
	@echo "Available commands:"
	@echo "  make build              - Build all modules (skip tests)"
	@echo "  make clean              - Clean all maven targets"
	@echo "  make compile            - Compile all modules"
	@echo "  make dev-up             - Start ONLY infrastructure (Postgres, Mongo, Kafka, Keycloak, MinIO)"
	@echo "  make dev-down           - Stop infrastructure"
	@echo "  make full-up            - Start ALL services and infra (Heavy!)"
	@echo "  make full-down          - Stop all services"
	@echo "  make infra-reset        - Remove all infra volumes and restart"
	@echo "  make logs-infra         - View infrastructure logs"
	@echo ""
	@echo "Run individual services (from root, no cd needed):"
	@echo "  make run-discovery      - Run discovery-server"
	@echo "  make run-config         - Run config-server"
	@echo "  make run-gateway        - Run api-gateway"
	@echo "  make run-identity       - Run identity-service"
	@echo "  make run-user           - Run user-service"
	@echo "  make run-product        - Run product-service"
	@echo "  make run-inventory      - Run inventory-service"
	@echo "  make run-order          - Run order-service"
	@echo "  make run-payment        - Run payment-service"
	@echo "  make run-notification   - Run notification-service"
	@echo "  make run-storage        - Run storage-service"

# --- Maven Commands ---
build:
	$(MVN) clean package -DskipTests

clean:
	$(MVN) clean

compile:
	$(MVN) clean compile

# --- Infrastructure Commands (Dành cho việc dev) ---
dev-up:
	docker compose -f $(DOCKER_INFRA) up -d

dev-down:
	docker compose -f $(DOCKER_INFRA) down

infra-reset:
	docker compose -f $(DOCKER_INFRA) down -v
	docker compose -f $(DOCKER_INFRA) up -d

logs-infra:
	docker compose -f $(DOCKER_INFRA) logs -f

# --- Full Stack Commands (Dành cho việc chạy toàn bộ hệ thống) ---
full-up:
	docker compose -f $(DOCKER_FULL) up -d --build

full-down:
	docker compose -f $(DOCKER_FULL) down

logs-full:
	docker compose -f $(DOCKER_FULL) logs -f

# --- Run Individual Services (from root, PowerShell compatible) ---
run-discovery:
	$(MVN) -pl discovery-server spring-boot:run

run-config:
	$(MVN) -pl config-server spring-boot:run

run-gateway:
	$(MVN) -pl api-gateway spring-boot:run

run-identity:
	$(MVN) -pl identity-service spring-boot:run

run-user:
	$(MVN) -pl user-service spring-boot:run

run-product:
	$(MVN) -pl product-service spring-boot:run

run-inventory:
	$(MVN) -pl inventory-service spring-boot:run

run-order:
	$(MVN) -pl order-service spring-boot:run

run-payment:
	$(MVN) -pl payment-service spring-boot:run

run-notification:
	$(MVN) -pl notification-service spring-boot:run

run-storage:
	$(MVN) -pl storage-service spring-boot:run

# --- Utility ---
# Lệnh clean container và image thừa (Dùng trong PowerShell)
prune:
	docker system prune -f
	docker volume prune -f
