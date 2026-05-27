def build_content_text(product: dict) -> str:
    name = str(product.get("name", ""))
    description = str(product.get("description", ""))
    primary_category = str(product.get("primaryCategorySlug") or product.get("primaryCategoryId") or "")
    category_slugs = " ".join(str(item) for item in product.get("categorySlugs", []) if item is not None)
    category_ids = " ".join(str(item) for item in product.get("categoryIds", []) if item is not None)
    brand = str(product.get("brandId") or "")
    specs = product.get("specs") or {}
    if isinstance(specs, dict):
        specs_text = " ".join(f"{key} {value}" for key, value in specs.items())
    else:
        specs_text = str(specs)

    return " ".join(
        part
        for part in [
            name,
            description,
            primary_category,
            category_slugs,
            category_ids,
            brand,
            specs_text,
        ]
        if part
    ).strip()
