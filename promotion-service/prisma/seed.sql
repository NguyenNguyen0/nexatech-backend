-- Promotion Service — Seed Data for Testing
-- Run via: make seed-promotion-ec2
-- Products (from MongoDB Atlas nexatech_products):
--   6a0c0d0a9c7843ff179575c1 — PC PV Gaming Cupid TM028  27,290,000 VND
--   6a0c0d0c9c7843ff179575c2 — PC PV Gaming Cupid TM030  30,590,000 VND
--   6a0c0d0e9c7843ff179575c3 — PC PV Gaming Hera H019    149,990,000 VND

BEGIN;

-- ─── Coupon Promotion ───────────────────────────────────────────────────────
-- Giảm 20%, tối đa 2 triệu, đơn tối thiểu 5 triệu
INSERT INTO promotions (
  id, code, name, description, type,
  "discountPercent", "minOrderValue", "maxDiscount",
  "usageLimit", "usageCount", "userLimit",
  "startTime", "endTime", status, "updatedAt"
) VALUES (
  'a1b2c3d4-0000-4000-a000-000000000001',
  'NEXASAVE20',
  'Giảm 20% cho đơn từ 5 triệu',
  'Áp dụng cho tất cả sản phẩm. Tối đa giảm 2,000,000đ.',
  'COUPON'::"PromotionType",
  20.00,
  5000000.00,
  2000000.00,
  500,
  0,
  1,
  NOW() - INTERVAL '1 hour',
  NOW() + INTERVAL '7 days',
  'ACTIVE'::"PromotionStatus",
  NOW()
) ON CONFLICT (id) DO NOTHING;

-- ─── Flash Sale Promotion 1 — Đang diễn ra ──────────────────────────────────
-- PC TM028 giảm 50%: 27,290,000 → 13,645,000 VND — 50 suất, đang chạy
INSERT INTO promotions (
  id, code, name, description, type,
  "discountPercent", "usageLimit", "usageCount", "userLimit",
  "startTime", "endTime", status, "updatedAt"
) VALUES (
  'a1b2c3d4-0000-4000-a000-000000000002',
  'FLASH-TM028-50',
  'Flash Sale PC Gaming TM028 — Giảm 50%',
  'Chỉ 50 suất duy nhất! PC Gaming i5-12400F + RTX 3060.',
  'FLASH_SALE'::"PromotionType",
  50.00,
  50,
  0,
  1,
  NOW() - INTERVAL '30 minutes',
  NOW() + INTERVAL '90 minutes',
  'ACTIVE'::"PromotionStatus",
  NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO flash_sales (
  id, "promotionId", "productId",
  "originalPrice", "salePrice",
  "totalStock", "remainingStock", "soldCount",
  "loadedToSpace", "updatedAt"
) VALUES (
  'b2c3d4e5-0000-4000-b000-000000000001',
  'a1b2c3d4-0000-4000-a000-000000000002',
  '6a0c0d0a9c7843ff179575c1',
  27290000.00,
  13645000.00,
  50,
  50,
  0,
  false,
  NOW()
) ON CONFLICT (id) DO NOTHING;

-- ─── Flash Sale Promotion 2 — Sắp diễn ra ──────────────────────────────────
-- PC TM030 giảm 35%: 30,590,000 → 19,900,000 VND — 20 suất, bắt đầu sau 2 giờ
INSERT INTO promotions (
  id, code, name, description, type,
  "discountPercent", "usageLimit", "usageCount", "userLimit",
  "startTime", "endTime", status, "updatedAt"
) VALUES (
  'a1b2c3d4-0000-4000-a000-000000000003',
  'FLASH-TM030-35',
  'Flash Sale PC Gaming TM030 — Giảm 35%',
  'Chỉ 20 suất! PC Gaming i5-12400F + RTX 5060 thế hệ mới.',
  'FLASH_SALE'::"PromotionType",
  35.00,
  20,
  0,
  1,
  NOW() + INTERVAL '2 hours',
  NOW() + INTERVAL '5 hours',
  'DRAFT'::"PromotionStatus",
  NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO flash_sales (
  id, "promotionId", "productId",
  "originalPrice", "salePrice",
  "totalStock", "remainingStock", "soldCount",
  "loadedToSpace", "updatedAt"
) VALUES (
  'b2c3d4e5-0000-4000-b000-000000000002',
  'a1b2c3d4-0000-4000-a000-000000000003',
  '6a0c0d0c9c7843ff179575c2',
  30590000.00,
  19900000.00,
  20,
  20,
  0,
  false,
  NOW()
) ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Verify
SELECT p.code, p.type, p.status, p."startTime", p."endTime",
       fs."productId", fs."originalPrice", fs."salePrice", fs."totalStock"
FROM promotions p
LEFT JOIN flash_sales fs ON fs."promotionId" = p.id
ORDER BY p."createdAt";
