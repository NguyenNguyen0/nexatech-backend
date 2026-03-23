// Chọn database
db = db.getSiblingDB('nexatech_products');

// =====================
// 1. Insert Brands
// =====================
db.brands.insertMany([
  {
    name: "Apple",
    slug: "apple",
    logoUrl: "https://logo.clearbit.com/apple.com",
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Samsung",
    slug: "samsung",
    logoUrl: "https://logo.clearbit.com/samsung.com",
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Xiaomi",
    slug: "xiaomi",
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
    price: NumberDecimal("29990000"),
    compareAtPrice: NumberDecimal("32990000"),
    sku: "IP15PRO-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "apple" })._id,
    images: ["https://dummyimage.com/600x600"],
    attributes: {
      color: "Titanium",
      storage: "256GB"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Samsung Galaxy S24",
    slug: "samsung-galaxy-s24",
    description: "Flagship Samsung",
    price: NumberDecimal("21990000"),
    compareAtPrice: NumberDecimal("24990000"),
    sku: "SS-S24-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "samsung" })._id,
    images: ["https://dummyimage.com/600x600"],
    attributes: {
      color: "Black",
      storage: "256GB"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  {
    name: "Xiaomi 14",
    slug: "xiaomi-14",
    description: "Flagship Xiaomi",
    price: NumberDecimal("15990000"),
    compareAtPrice: NumberDecimal("17990000"),
    sku: "MI14-256",
    categoryId: db.categories.findOne({ slug: "dien-thoai" })._id,
    brandId: db.brands.findOne({ slug: "xiaomi" })._id,
    images: ["https://dummyimage.com/600x600"],
    attributes: {
      color: "White",
      storage: "256GB"
    },
    active: true,
    createdAt: new Date(),
    updatedAt: new Date()
  }
]);

print("✅ Init database nexatech_products DONE");