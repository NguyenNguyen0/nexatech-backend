-- =============================================================================
-- SEED: nexatech_payments — Test payments linked to test orders
-- =============================================================================
-- HOW TO RUN:
--   docker exec -i test-postgres-payment psql -U nexatech -d nexatech_payments < postgres-init/03_seed_test_payments.sql
--
-- Run AFTER 02_seed_test_orders.sql — uses same order UUIDs.
-- Replace the customer_id below with your actual Keycloak user UUID.
-- =============================================================================

\set customer_id '00000000-0000-0000-0000-000000000001'

\set order1_id  'a0000001-0000-0000-0000-000000000001'
\set order2_id  'a0000002-0000-0000-0000-000000000001'
\set order3_id  'a0000003-0000-0000-0000-000000000001'
\set order4_id  'a0000004-0000-0000-0000-000000000001'
\set order5_id  'a0000005-0000-0000-0000-000000000001'
\set order6_id  'a0000006-0000-0000-0000-000000000001'

INSERT INTO payments (
  id, order_id, customer_id,
  amount, method, status, transaction_id, failure_reason,
  created_at, updated_at
) VALUES

-- Order 1: DELIVERED — VNPAY COMPLETED
('c0000001-0000-0000-0000-000000000001'::uuid, :'order1_id'::uuid, :'customer_id'::uuid,
 36510000, 'VNPAY', 'COMPLETED', 'VNP14220260001009101', NULL,
 NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),

-- Order 2: SHIPPED — COD PENDING
('c0000002-0000-0000-0000-000000000001'::uuid, :'order2_id'::uuid, :'customer_id'::uuid,
 55020000, 'COD', 'PENDING', NULL, NULL,
 NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

-- Order 3: CONFIRMED — VNPAY COMPLETED
('c0000003-0000-0000-0000-000000000001'::uuid, :'order3_id'::uuid, :'customer_id'::uuid,
 32030000, 'VNPAY', 'COMPLETED', 'VNP14220260001028301', NULL,
 NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

-- Order 4: PENDING — VNPAY PENDING (payment initiated but not confirmed)
('c0000004-0000-0000-0000-000000000001'::uuid, :'order4_id'::uuid, :'customer_id'::uuid,
 13030000, 'VNPAY', 'PENDING', NULL, NULL,
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),

-- Order 5: CANCELLED — VNPAY FAILED
('c0000005-0000-0000-0000-000000000001'::uuid, :'order5_id'::uuid, :'customer_id'::uuid,
 29990000, 'VNPAY', 'FAILED', 'VNP14220260001003501', 'Giao dịch bị từ chối bởi ngân hàng',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),

-- Order 6: PROCESSING — COD PENDING
('c0000006-0000-0000-0000-000000000001'::uuid, :'order6_id'::uuid, :'customer_id'::uuid,
 18530000, 'COD', 'PENDING', NULL, NULL,
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days')

ON CONFLICT (id) DO NOTHING;

\echo '✓ Seeded 6 test payments into nexatech_payments'
