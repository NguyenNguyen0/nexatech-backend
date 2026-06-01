from __future__ import annotations

from collections import Counter, defaultdict
from threading import Lock
from typing import Any


class DelegationMetrics:
    """In-memory counters for Phase D delegation observability."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._totals: Counter[str] = Counter()
        self._by_domain: dict[str, Counter[str]] = defaultdict(Counter)

    def record(
        self,
        metric: str,
        *,
        domain: str | None = None,
        increment: int = 1,
    ) -> None:
        normalized_domain = (domain or "unknown").strip().lower() or "unknown"
        with self._lock:
            self._totals[metric] += increment
            self._by_domain[normalized_domain][metric] += increment

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            totals = dict(self._totals)
            by_domain = {
                domain: dict(counter)
                for domain, counter in sorted(self._by_domain.items())
            }

        return {
            "totals": totals,
            "byDomain": by_domain,
        }

    def reset(self) -> None:
        with self._lock:
            self._totals.clear()
            self._by_domain.clear()


DELEGATION_METRICS = DelegationMetrics()
