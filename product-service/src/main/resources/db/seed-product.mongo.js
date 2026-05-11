// Chọn toàn bộ rồi -> Run Script -> Thêm datasource -> Chọn run

// Chọn database
db = db.getSiblingDB('nexatech_products');

// =====================
// 1. Insert Brands
// =====================
db.brands.insertMany([
  {
    name: "Apple",
    slug: "apple",
    parentBrandId: null,
    logoUrl: "https://logo.clearbit.com/apple.com",
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Samsung",
    slug: "samsung",
    parentBrandId: null,
    logoUrl: "https://logo.clearbit.com/samsung.com",
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Xiaomi",
    slug: "xiaomi",
    parentBrandId: null,
    logoUrl: "https://logo.clearbit.com/mi.com",
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  }
]);

// =====================
// 2. Insert Categories
// =====================
db.categories.insertMany([
  {
    name: "Điện thoại",
    slug: "dien-thoai",
    description: "Danh mục điện thoại",
    parentId: null,
    imageUrl: "https://dummyimage.com/300x300",
    sortOrder: 1,
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Laptop",
    slug: "laptop",
    description: "Danh mục laptop",
    parentId: null,
    imageUrl: "https://dummyimage.com/300x300",
    sortOrder: 2,
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  }
]);

// =====================
// 3. Insert Products (query trực tiếp ID)
// =====================
db.products.insertMany([
  {
    name: "iPhone 15 Pro",
    slug: "iphone-15-pro",
    description: "Điện thoại Apple mới nhất",
    descriptionHtml: "<p>Điện thoại Apple mới nhất</p>",
    price: NumberDecimal("29990000"),
    compareAtPrice: NumberDecimal("32990000"),
    sku: "IP15PRO-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "apple" })._id,
    thumbnail: "https://dummyimage.com/600x600",
    images: ["https://dummyimage.com/600x600"],
    specs: {
      chipset: "Apple A17 Pro",
      ram: "8GB",
      storage: "256GB"
    },
    tags: ["new-arrival", "hot-deal"],
    attributes: {
      color: "Titanium",
      storage: "256GB"
    },
    metadata: {
      source: "seed-script",
      externalId: "IP15PRO-256",
      sourceUrl: "https://example.com/products/iphone-15-pro"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Samsung Galaxy S24",
    slug: "samsung-galaxy-s24",
    description: "Flagship Samsung",
    descriptionHtml: "<p>Flagship Samsung</p>",
    price: NumberDecimal("21990000"),
    compareAtPrice: NumberDecimal("24990000"),
    sku: "SS-S24-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "samsung" })._id,
    thumbnail: "https://dummyimage.com/600x600",
    images: ["https://dummyimage.com/600x600"],
    specs: {
      chipset: "Snapdragon 8 Gen 3",
      ram: "12GB",
      storage: "256GB"
    },
    tags: ["new-arrival", "android"],
    attributes: {
      color: "Black",
      storage: "256GB"
    },
    metadata: {
      source: "seed-script",
      externalId: "SS-S24-256",
      sourceUrl: "https://example.com/products/samsung-galaxy-s24"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Xiaomi 14",
    slug: "xiaomi-14",
    description: "Flagship Xiaomi",
    descriptionHtml: "<p>Flagship Xiaomi</p>",
    price: NumberDecimal("15990000"),
    compareAtPrice: NumberDecimal("17990000"),
    sku: "MI14-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "xiaomi" })._id,
    thumbnail: "https://dummyimage.com/600x600",
    images: ["https://dummyimage.com/600x600"],
    specs: {
      chipset: "Snapdragon 8 Gen 3",
      ram: "12GB",
      storage: "256GB"
    },
    tags: ["gaming", "android"],
    attributes: {
      color: "White",
      storage: "256GB"
    },
    metadata: {
      source: "seed-script",
      externalId: "MI14-256",
      sourceUrl: "https://example.com/products/xiaomi-14"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  }
]);

print("✅ Init database nexatech_products DONE");