-- ============================================================
-- Auto-create databases on first Postgres startup
-- (mounted to /docker-entrypoint-initdb.d/)
-- NOTE: Script chỉ chạy 1 lần khi volume được tạo mới.
--       CREATE DATABASE không chạy được trong transaction block,
--       nên mỗi lệnh là một statement riêng biệt.
-- ============================================================

-- Identity Service
CREATE DATABASE nexatech_identity WITH OWNER nexatech;

-- User Service
CREATE DATABASE nexatech_users WITH OWNER nexatech;

-- Order Service
CREATE DATABASE nexatech_orders WITH OWNER nexatech;

-- Payment Service
CREATE DATABASE nexatech_payments WITH OWNER nexatech;

-- Shipping Service
CREATE DATABASE nexatech_shipping WITH OWNER nexatech;

-- Inventory Service
CREATE DATABASE nexatech_inventory WITH OWNER nexatech;

-- Notification Service
CREATE DATABASE nexatech_notifications WITH OWNER nexatech;

-- Chat Service
CREATE DATABASE nexatech_chats WITH OWNER nexatech;

-- News Service
CREATE DATABASE nexatech_news WITH OWNER nexatech;

-- Promotion Service
CREATE DATABASE nexatech_promotions WITH OWNER nexatech;

-- Agent Service (LangGraph checkpointer)
CREATE DATABASE nexatech_agent WITH OWNER nexatech;
