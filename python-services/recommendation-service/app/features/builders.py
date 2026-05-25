def build_content_text(product: dict) -> str:
    name = str(product.get("name", ""))
    description = str(product.get("description", ""))
    return f"{name} {description}".strip()
