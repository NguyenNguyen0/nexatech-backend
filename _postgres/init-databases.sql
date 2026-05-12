-- ============================================================
-- Auto-create databases on first Postgres startup
-- (mounted to /docker-entrypoint-initdb.d/)
-- ============================================================

-- Keycloak IAM
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak');

-- Identity Service
SELECT 'CREATE DATABASE nexatech_identity'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_identity');

-- User Service
SELECT 'CREATE DATABASE nexatech_users'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_users');

-- Order Service
SELECT 'CREATE DATABASE nexatech_orders'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_orders');

-- Payment Service
SELECT 'CREATE DATABASE nexatech_payments'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_payments');

-- Inventory Service
SELECT 'CREATE DATABASE nexatech_inventory'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_inventory');

-- Notification Service
SELECT 'CREATE DATABASE nexatech_notifications'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_notifications');

-- Chat Service
SELECT 'CREATE DATABASE nexatech_chats'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nexatech_chats');
