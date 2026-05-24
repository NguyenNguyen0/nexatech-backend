# ================================================================================================
# NEXATECH BACKEND - PROJECT MANAGEMENT (Windows/PowerShell Compatible)
# ================================================================================================

# Biến mặc định
DOCKER_INFRA = docker-compose.infra.yml
DOCKER_FULL = docker-compose.yml
DOCKER_CORE = _realms/docker-compose-order-payment.yml
MVN = mvn

# Load .env.ec2 và set env vars trong cùng một PowerShell process
LOAD_EC2 = Get-Content .env.ec2 | Where-Object { $$_ -notmatch '^\s*\#' -and $$_ -match '=' } | ForEach-Object { $$p = $$_ -split '=',2; [Environment]::SetEnvironmentVariable($$p[0].Trim(),$$p[1].Trim(),'Process') }

.PHONY: help build clean compile dev-up dev-down full-up full-down infra-reset logs-infra logs-full prune \
	core-up core-down core-build core-logs \
	run-discovery run-config run-gateway \
	run-identity run-user run-product run-inventory run-order run-payment run-notification run-chat run-storage \
	run-gateway-ec2 run-identity-ec2 run-user-ec2 run-product-ec2 run-inventory-ec2 \
	run-order-ec2 run-payment-ec2 run-shipping-ec2 run-notification-ec2 run-chat-ec2 run-storage-ec2 run-news-ec2

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
	@echo "  make run-storage        - Run storage-service"
	@echo ""
	@echo "Run with EC2 infra (loads .env.ec2):"
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
	@echo "  make run-news-ec2         - Run news-service (EC2 infra)"

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

run-storage:
	$(MVN) -pl storage-service spring-boot:run

# --- Run Individual Services with EC2 Infra (.env.ec2) ---
run-gateway-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); mvn -pl api-gateway spring-boot:run"

run-product-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); mvn -pl product-service spring-boot:run"

run-inventory-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); mvn -pl inventory-service spring-boot:run"

run-identity-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); mvn -pl identity-service spring-boot:run"

run-storage-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); mvn -pl storage-service spring-boot:run"

run-order-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:SPRING_DATASOURCE_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_orders'; mvn -pl order-service spring-boot:run"

run-payment-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:SPRING_DATASOURCE_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_payments'; mvn -pl payment-service spring-boot:run"

run-shipping-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:SPRING_DATASOURCE_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_shipping'; mvn -pl shipping-service spring-boot:run"

run-notification-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:DB_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_notifications'; mvn -pl notification-service spring-boot:run"

run-chat-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:DB_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_chats'; mvn -pl chat-service spring-boot:run"

run-user-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:DATASOURCE_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_users'; $$env:DATASOURCE_USERNAME=$$env:DB_USERNAME; $$env:DATASOURCE_PASSWORD=$$env:DB_PASSWORD; $$env:KAFKA_GROUP_ID='user-service-group'; mvn -pl user-service spring-boot:run"

run-news-ec2:
	powershell -NoProfile -Command "$(LOAD_EC2); $$env:SPRING_DATASOURCE_URL='jdbc:postgresql://'+$$env:EC2_HOST+':5432/nexatech_news'; $$env:SPRING_DATASOURCE_USERNAME=$$env:DB_USERNAME; $$env:SPRING_DATASOURCE_PASSWORD=$$env:DB_PASSWORD; mvn -pl news-service spring-boot:run"

# --- Utility ---
# Lệnh clean container và image thừa (Dùng trong PowerShell)
prune:
	docker system prune -f
	docker volume prune -f
