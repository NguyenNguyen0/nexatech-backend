# ================================================================================================
# NEXATECH BACKEND - PROJECT MANAGEMENT (Windows/PowerShell Compatible)
# ================================================================================================

# Biến mặc định
DOCKER_INFRA = docker-compose.infra.yml
DOCKER_FULL = docker-compose.yml
DOCKER_CORE = _realms/docker-compose-order-payment.yml
MVN = mvn
START_EC2_SH = ./_scripts/start-ec2.sh
SHELL := /bin/bash

# Load .env.ec2 và set env vars trong cùng một PowerShell process
# Load .env (service credentials: GHN, VNPay, MinIO, v.v.)

.PHONY: help build clean compile dev-up dev-down full-up full-down infra-reset logs-infra logs-full prune \
	core-up core-down core-build core-logs \
	run-discovery run-config run-gateway \
	run-identity run-user run-product run-inventory run-order run-payment run-notification run-chat run-audit run-storage run-promotion run-mcp run-mcp-meta run-mcp-inventory run-mcp-product run-mcp-order run-mcp-payment run-mcp-promotion run-mcp-news run-mcp-audit run-recommendation \
	run-gateway-test run-chat-test \
	run-gateway-ec2 run-identity-ec2 run-user-ec2 run-product-ec2 run-inventory-ec2 \
	run-order-ec2 run-payment-ec2 run-shipping-ec2 run-notification-ec2 run-chat-ec2 run-audit-ec2 run-storage-ec2 run-news-ec2 run-promotion-ec2 run-recommendation-ec2 \
	run-gateway-test-sh run-chat-test-sh \
	run-gateway-ec2-sh run-identity-ec2-sh run-user-ec2-sh run-product-ec2-sh run-inventory-ec2-sh \
	run-order-ec2-sh run-payment-ec2-sh run-shipping-ec2-sh run-notification-ec2-sh run-chat-ec2-sh run-audit-ec2-sh run-storage-ec2-sh run-news-ec2-sh run-promotion-ec2-sh run-recommendation-ec2-sh \
	migrate-promotion-ec2 migrate-promotion-ec2-sh seed-promotion-ec2 seed-promotion-ec2-sh \
	run-all-terminals run-all-ec2-terminals run-store-ec2-terminals run-chat-ec2-terminals run-order-ec2-terminals

help:
	@echo "Available commands:"
	@echo "  make build              - Build all modules (skip tests)"
	@echo "  make clean              - Clean all maven targets"
	@echo "  make compile            - Compile all modules"
	@echo "  make dev-up             - Start ONLY infrastructure (Postgres, Mongo, Kafka, Keycloak, MinIO)"
	@echo "  make dev-down           - Stop infrastructure"
	@echo "  make full-up            - Start ALL services and infra (Heavy!)"
	@echo "  make full-down          - Stop all services"
	@echo "  make core-up            - Build + start core services (product/inventory/order/payment/shipping + infra)"
	@echo "  make core-build         - Build only core services images (no start)"
	@echo "  make core-down          - Stop core services"
	@echo "  make core-logs          - Stream logs from core services"
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
	@echo "  make run-chat           - Run chat-service"
	@echo "  make run-audit          - Run audit-service"
	@echo "  make run-storage        - Run storage-service"
	@echo "  make run-promotion      - Run promotion-service (Node.js, requires .env in promotion-service/)"
	@echo "  make run-mcp            - Run mcp-server (Python/FastAPI + FastMCP)"
	@echo "  make run-recommendation - Run recommendation-service (Python/FastAPI)"
	@echo ""
	@echo "Run with EC2 infra (loads .env.ec2):"
	@echo "  make run-gateway-test    - Alias for run-gateway-ec2 (client REST uses this)"
	@echo "  make run-chat-test       - Alias for run-chat-ec2 (client chat REST via gateway)"
	@echo "  make run-gateway-ec2      - Run api-gateway (EC2 infra)"
	@echo "  make run-product-ec2      - Run product-service (EC2 infra)"
	@echo "  make run-inventory-ec2    - Run inventory-service (EC2 infra)"
	@echo "  make run-order-ec2        - Run order-service (EC2 infra)"
	@echo "  make run-payment-ec2      - Run payment-service (EC2 infra)"
	@echo "  make run-shipping-ec2     - Run shipping-service (EC2 infra)"
	@echo "  make run-user-ec2         - Run user-service (EC2 infra)"
	@echo "  make run-identity-ec2     - Run identity-service (EC2 infra)"
	@echo "  make run-notification-ec2 - Run notification-service (EC2 infra)"
	@echo "  make run-chat-ec2         - Run chat-service (EC2 infra)"
	@echo "  make run-storage-ec2      - Run storage-service (EC2 infra)"
	@echo "  make run-audit-ec2        - Run audit-service (EC2 infra)"
	@echo "  make run-news-ec2         - Run news-service (EC2 infra)"
	@echo "  make run-promotion-ec2    - Run promotion-service (EC2 infra, Node.js)"
	@echo "  make run-recommendation-ec2 - Run recommendation-service (EC2 infra, Python/FastAPI)"
	@echo ""
	@echo "Run with EC2 infra via Bash start-ec2.sh:"
	@echo "  make run-gateway-ec2-sh      - Run api-gateway (EC2 infra)"
	@echo "  make run-product-ec2-sh      - Run product-service (EC2 infra)"
	@echo "  make run-inventory-ec2-sh    - Run inventory-service (EC2 infra)"
	@echo "  make run-order-ec2-sh        - Run order-service (EC2 infra)"
	@echo "  make run-payment-ec2-sh      - Run payment-service (EC2 infra)"
	@echo "  make run-shipping-ec2-sh     - Run shipping-service (EC2 infra)"
	@echo "  make run-user-ec2-sh         - Run user-service (EC2 infra)"
	@echo "  make run-identity-ec2-sh     - Run identity-service (EC2 infra)"
	@echo "  make run-notification-ec2-sh - Run notification-service (EC2 infra)"
	@echo "  make run-chat-ec2-sh         - Run chat-service (EC2 infra)"
	@echo "  make run-storage-ec2-sh      - Run storage-service (EC2 infra)"
	@echo "  make run-audit-ec2-sh        - Run audit-service (EC2 infra)"
	@echo "  make run-news-ec2-sh         - Run news-service (EC2 infra)"
	@echo "  make run-promotion-ec2-sh    - Run promotion-service (EC2 infra, Node.js)"
	@echo "  make run-recommendation-ec2-sh - Run recommendation-service (EC2 infra, Python/FastAPI)"

	@echo ""
	@echo "Open services in separate PowerShell terminals:"
	@echo "  make run-all-ec2-terminals   - Open all EC2-mode services, one terminal each"
	@echo "  make run-all-terminals       - Open all local-mode services, one terminal each"
	@echo "  make run-store-ec2-terminals - Open gateway/product/inventory/promotion, one terminal each"
	@echo "  make run-chat-ec2-terminals  - Open gateway/chat/user/identity, one terminal each"
	@echo "  make run-order-ec2-terminals - Open gateway/order/payment/shipping, one terminal each"

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

# --- Core Stack (product + inventory + order + payment + shipping) ---
# Đọc .env từ nexatech-backend/ (--env-file .env) để substitute biến trong compose file
core-up:
	docker compose --env-file .env -f $(DOCKER_CORE) up -d --build

core-build:
	docker compose --env-file .env -f $(DOCKER_CORE) build

core-down:
	docker compose --env-file .env -f $(DOCKER_CORE) down

core-logs:
	docker compose --env-file .env -f $(DOCKER_CORE) logs -f

# --- Full Stack Commands (Dành cho việc chạy toàn bộ hệ thống) ---
full-up:
	docker compose -f $(DOCKER_FULL) up -d --build

full-down:
	docker compose -f $(DOCKER_FULL) down

logs-full:
	docker compose -f $(DOCKER_FULL) logs -f

# --- Run Individual Services (from root, PowerShell compatible) ---
spring-service:
	@test -f "$(ENV_FILE)" || (echo "ENV_FILE not found: $(ENV_FILE)" && exit 1)
	@echo "Starting $(SERVICE) with $(ENV_FILE)"
	@set -a && \
	. ./$(ENV_FILE) && \
	set +a && \
	$(MVN) -pl $(SERVICE) spring-boot:run

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

run-chat:
	$(MVN) -pl chat-service spring-boot:run

run-audit:
	$(MVN) -pl audit-service spring-boot:run

run-news:
	$(MVN) -pl news-service spring-boot:run

run-storage:
	$(MVN) -pl storage-service spring-boot:run

run-promotion:
	powershell -NoProfile -Command "Set-Location promotion-service; npm run start:dev"

run-mcp:
	cd python-services/mcp-server && uv run main.py

run-mcp-meta:
	cd python-services/mcp-server && SERVICE_NAME=meta-mcp MCP_SERVER_PROFILE=meta PORT=8094 uv run main.py

run-mcp-inventory:
	cd python-services/mcp-server && SERVICE_NAME=inventory-mcp MCP_SERVER_PROFILE=inventory PORT=8101 uv run main.py

run-mcp-product:
	cd python-services/mcp-server && SERVICE_NAME=product-mcp MCP_SERVER_PROFILE=product PORT=8102 uv run main.py

run-mcp-order:
	cd python-services/mcp-server && SERVICE_NAME=order-mcp MCP_SERVER_PROFILE=order PORT=8103 uv run main.py

run-mcp-payment:
	cd python-services/mcp-server && SERVICE_NAME=payment-mcp MCP_SERVER_PROFILE=payment PORT=8104 uv run main.py

run-mcp-promotion:
	cd python-services/mcp-server && SERVICE_NAME=promotion-mcp MCP_SERVER_PROFILE=promotion PORT=8105 uv run main.py

run-mcp-news:
	cd python-services/mcp-server && SERVICE_NAME=news-mcp MCP_SERVER_PROFILE=news PORT=8106 uv run main.py

run-mcp-audit:
	cd python-services/mcp-server && SERVICE_NAME=audit-mcp MCP_SERVER_PROFILE=audit PORT=8107 uv run main.py

run-recommendation:
	powershell -NoProfile -Command "Set-Location python-services/recommendation-service; uv run main.py"

# --- Chat test via local app + EC2 infra ---
# Use these when the frontend points REST to localhost:8080 and infra/Eureka lives on EC2.
run-gateway-test: run-gateway-ec2

run-chat-test: run-chat-ec2

# --- Run Individual Services with EC2 Infra (.env.ec2) ---
run-gateway-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 api-gateway

run-product-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 product-service

run-inventory-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 inventory-service

run-identity-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 identity-service

run-storage-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 storage-service

run-order-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 order-service

run-payment-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 payment-service

run-shipping-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 shipping-service

run-notification-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 notification-service

run-chat-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 chat-service

run-audit-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 audit-service

run-user-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 user-service

run-news-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 news-service

run-promotion-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 promotion-service

run-recommendation-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 recommendation-service

migrate-promotion-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 promotion-migrate

seed-promotion-ec2:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-ec2.ps1 promotion-seed

# --- Run Individual Services with EC2 Infra via Bash (.env.ec2) ---
run-gateway-test-sh: run-gateway-ec2-sh

run-chat-test-sh: run-chat-ec2-sh

run-gateway-ec2-sh:
	$(START_EC2_SH) api-gateway

run-product-ec2-sh:
	$(START_EC2_SH) product-service

run-inventory-ec2-sh:
	$(START_EC2_SH) inventory-service

run-identity-ec2-sh:
	$(START_EC2_SH) identity-service

run-storage-ec2-sh:
	$(START_EC2_SH) storage-service

run-order-ec2-sh:
	$(START_EC2_SH) order-service

run-payment-ec2-sh:
	$(START_EC2_SH) payment-service

run-shipping-ec2-sh:
	$(START_EC2_SH) shipping-service

run-notification-ec2-sh:
	$(START_EC2_SH) notification-service

run-chat-ec2-sh:
	$(START_EC2_SH) chat-service

run-audit-ec2-sh:
	$(START_EC2_SH) audit-service

run-user-ec2-sh:
	$(START_EC2_SH) user-service

run-news-ec2-sh:
	$(START_EC2_SH) news-service

run-promotion-ec2-sh:
	$(START_EC2_SH) promotion-service

run-recommendation-ec2-sh:
	$(START_EC2_SH) recommendation-service

migrate-promotion-ec2-sh:
	$(START_EC2_SH) promotion-migrate

seed-promotion-ec2-sh:
	$(START_EC2_SH) promotion-seed

# --- Open Multiple Services In Separate Terminals ---
run-all-ec2-terminals:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-services.ps1 -Mode ec2

run-all-terminals:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-services.ps1 -Mode local

run-store-ec2-terminals:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-services.ps1 -Mode ec2 -Services api-gateway,product-service,inventory-service,promotion-service

run-chat-ec2-terminals:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-services.ps1 -Mode ec2 -Services api-gateway,chat-service,user-service,identity-service

run-order-ec2-terminals:
	powershell -NoProfile -ExecutionPolicy Bypass -File ./_scripts/start-services.ps1 -Mode ec2 -Services api-gateway,order-service,payment-service,shipping-service

# --- Utility ---
# Lệnh clean container và image thừa (Dùng trong PowerShell)
prune:
	docker system prune -f
	docker volume prune -f
