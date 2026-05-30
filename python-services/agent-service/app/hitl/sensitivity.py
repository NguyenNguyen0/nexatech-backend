from __future__ import annotations


TOOL_SENSITIVITY: dict[str, str] = {
    "create_news_article": "medium",
    "update_article_status": "medium",
    "search_audit_logs": "low",
    "get_revenue_stats": "medium",
    "get_sales_summary": "medium",
    "export_report_pdf": "medium",
    "export_report_xlsx": "medium",
    "list_promotions": "low",
    "preview_flash_sale": "low",
    "create_promotion": "high",
    "confirm_flash_sale": "high",
    "list_inventory": "low",
    "get_inventory": "low",
    "get_inventory_movements": "low",
    "check_inventory": "low",
    "get_low_stock_items": "low",
    "update_stock": "high",
    "reserve_stock": "high",
    "release_stock": "high",
    "bulk_import_inventory": "high",
    "update_warehouse_info": "medium",
}
