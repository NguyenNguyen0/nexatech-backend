-- =============================================================================
-- SEED: nexatech_orders — Test orders + order items
-- =============================================================================
-- HOW TO RUN:
--   docker exec -i test-postgres-order psql -U nexatech -d nexatech_orders < postgres-init/02_seed_test_orders.sql
--
-- IMPORTANT: Replace the customer_id below with your actual Keycloak user UUID.
--   Find it at: http://localhost:8080 → Admin → Nexatech realm → Users → [your user] → ID
-- =============================================================================

\set customer_id '00000000-0000-0000-0000-000000000001'

-- Fixed order UUIDs (idempotent — safe to run multiple times)
\set order1_id  'a0000001-0000-0000-0000-000000000001'
\set order2_id  'a0000002-0000-0000-0000-000000000001'
\set order3_id  'a0000003-0000-0000-0000-000000000001'
\set order4_id  'a0000004-0000-0000-0000-000000000001'
\set order5_id  'a0000005-0000-0000-0000-000000000001'
\set order6_id  'a0000006-0000-0000-0000-000000000001'

-- ─── Orders ───────────────────────────────────────────────────────────────────

INSERT INTO orders (
  id, order_number, customer_id, status,
  total_amount, shipping_fee, discount,
  shipping_address, shipping_method, payment_method,
  tracking_number, carrier_name,
  shipped_at, delivered_at, estimated_delivery,
  created_at, updated_at
) VALUES

-- 1) DELIVERED — VNPAY (iPhone 15 Pro + AirPods Pro)
(:'order1_id'::uuid, 'ORD-TEST-001', :'customer_id'::uuid, 'DELIVERED',
 36510000, 30000, 0,
 '15 Lê Lợi, Quận 1, TP.HCM', 'STANDARD', 'VNPAY',
 'GHN2026001001', 'Giao hàng nhanh (GHN)',
 NOW() - INTERVAL '8 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '5 days'),

-- 2) SHIPPED — COD (MacBook Pro 14")
(:'order2_id'::uuid, 'ORD-TEST-002', :'customer_id'::uuid, 'SHIPPED',
 55020000, 30000, 0,
 '72 Nguyễn Huệ, Quận 1, TP.HCM', 'STANDARD', 'COD',
 'GHTK2026002001', 'Giao hàng tiết kiệm (GHTK)',
 NOW() - INTERVAL '2 days', NULL, NOW() + INTERVAL '1 day',
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days'),

-- 3) CONFIRMED — VNPAY (iPad Air M2 + Apple Watch Series 9)
(:'order3_id'::uuid, 'ORD-TEST-003', :'customer_id'::uuid, 'CONFIRMED',
 32030000, 30000, 500000,
 '201 Trần Hưng Đạo, Quận 5, TP.HCM', 'EXPRESS', 'VNPAY',
 NULL, NULL, NULL, NULL, NULL,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

-- 4) PENDING — VNPAY (AirPods Pro 2nd Gen x2)
(:'order4_id'::uuid, 'ORD-TEST-004', :'customer_id'::uuid, 'PENDING',
 13030000, 30000, 0,
 '88 Đinh Tiên Hoàng, Bình Thạnh, TP.HCM', 'STANDARD', 'VNPAY',
 NULL, NULL, NULL, NULL, NULL,
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),

-- 5) CANCELLED — VNPAY (iPhone 15 Pro)
(:'order5_id'::uuid, 'ORD-TEST-005', :'customer_id'::uuid, 'CANCELLED',
 29990000, 0, 0,
 '33 Lý Thường Kiệt, Quận 10, TP.HCM', 'STANDARD', 'VNPAY',
 NULL, NULL, NULL, NULL, NULL,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),

-- 6) PROCESSING — COD (Apple Watch + AirPods)
(:'order6_id'::uuid, 'ORD-TEST-006', :'customer_id'::uuid, 'PROCESSING',
 18530000, 50000, 0,
 '10 Phan Xích Long, Phú Nhuận, TP.HCM', 'EXPRESS', 'COD',
 NULL, NULL, NULL, NULL, NULL,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')

ON CONFLICT (id) DO NOTHING;


-- ─── Order Items ──────────────────────────────────────────────────────────────

INSERT INTO order_items (
  id, order_id, product_id, product_name, quantity, unit_price, subtotal,
  created_at, updated_at
) VALUES

-- Order 1: iPhone 15 Pro + AirPods Pro
('b0000101-0000-0000-0000-000000000001'::uuid, :'order1_id'::uuid,
 'prod-iphone15pro-256', 'iPhone 15 Pro 256GB - Natural Titanium', 1, 29990000, 29990000,
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
('b0000102-0000-0000-0000-000000000001'::uuid, :'order1_id'::uuid,
 'prod-airpodspro2', 'AirPods Pro (2nd generation) với USB‑C', 1, 6490000, 6490000,
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),

-- Order 2: MacBook Pro 14"
('b0000201-0000-0000-0000-000000000001'::uuid, :'order2_id'::uuid,
 'prod-macbookpro14-m3', 'MacBook Pro 14" M3 256GB — Space Black', 1, 54990000, 54990000,
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

-- Order 3: iPad Air M2 + Apple Watch Series 9
('b0000301-0000-0000-0000-000000000001'::uuid, :'order3_id'::uuid,
 'prod-ipadair-m2', 'iPad Air 11" (M2) 256GB — Xanh lam', 1, 19990000, 19990000,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('b0000302-0000-0000-0000-000000000001'::uuid, :'order3_id'::uuid,
 'prod-applewatchs9-41', 'Apple Watch Series 9 GPS 41mm — Midnight', 1, 11990000, 11990000,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

-- Order 4: AirPods Pro x2
('b0000401-0000-0000-0000-000000000001'::uuid, :'order4_id'::uuid,
 'prod-airpodspro2', 'AirPods Pro (2nd generation) với USB‑C', 2, 6490000, 12980000,
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),

-- Order 5: iPhone 15 Pro (cancelled)
('b0000501-0000-0000-0000-000000000001'::uuid, :'order5_id'::uuid,
 'prod-iphone15pro-256', 'iPhone 15 Pro 256GB - Natural Titanium', 1, 29990000, 29990000,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),

-- Order 6: Apple Watch + AirPods
('b0000601-0000-0000-0000-000000000001'::uuid, :'order6_id'::uuid,
 'prod-applewatchs9-41', 'Apple Watch Series 9 GPS 41mm — Midnight', 1, 11990000, 11990000,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
('b0000602-0000-0000-0000-000000000001'::uuid, :'order6_id'::uuid,
 'prod-airpodspro2', 'AirPods Pro (2nd generation) với USB‑C', 1, 6490000, 6490000,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')

ON CONFLICT (id) DO NOTHING;

\echo '✓ Seeded 6 test orders and 9 order items into nexatech_orders'
