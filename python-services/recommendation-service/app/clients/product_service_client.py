import httpx


class ProductServiceClient:
    def __init__(
        self,
        base_url: str,
        catalog_path: str,
        lookup_path: str,
        api_key: str,
        api_key_header: str,
        timeout_seconds: float = 30.0,
    ) -> None:
        self._catalog_path = catalog_path
        self._lookup_path = lookup_path
        self._api_key_header = api_key_header
        self._api_key = api_key
        self._client = httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=timeout_seconds)

    async def fetch_products_page(self, page: int, size: int) -> tuple[list[dict], bool]:
        response = await self._client.get(
            self._catalog_path,
            params={"page": page, "size": size},
            headers={self._api_key_header: self._api_key},
        )
        response.raise_for_status()

        payload = response.json()
        data = payload.get("data") or {}
        content = data.get("content") or []
        is_last = bool(data.get("last", True))
        return content, is_last

    async def fetch_products_by_ids(self, product_ids: list[str]) -> dict[str, dict]:
        ids = [str(product_id).strip() for product_id in product_ids if str(product_id).strip()]
        if not ids:
            return {}

        response = await self._client.get(
            self._lookup_path,
            params=[("ids", product_id) for product_id in ids],
            headers={self._api_key_header: self._api_key},
        )
        response.raise_for_status()

        payload = response.json()
        items = payload.get("data") or []
        return {str(item.get("id")): item for item in items if item.get("id")}

    async def close(self) -> None:
        await self._client.aclose()
