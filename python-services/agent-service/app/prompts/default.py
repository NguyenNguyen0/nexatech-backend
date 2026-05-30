from __future__ import annotations

from functools import lru_cache
from pathlib import Path


_PROMPT_FILE = Path(__file__).with_name("system_prompt.md")


@lru_cache(maxsize=1)
def load_default_system_prompt() -> str:
    try:
        return _PROMPT_FILE.read_text(encoding="utf-8").strip()
    except Exception:
        return "Bạn là NexaTech Assistant. Luôn ưu tiên chính xác và minh bạch dữ liệu."
