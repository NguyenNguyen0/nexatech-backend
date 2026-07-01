from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import SETTINGS
from app.graph.llm_factory import get_chat_model, llm_provider_configured


def test_llm_provider_configured_false_when_groq_key_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(SETTINGS, "llm_provider", "groq")
    monkeypatch.setattr(SETTINGS, "groq_api_key", None)

    assert llm_provider_configured() is False


def test_llm_provider_configured_true_for_openai_key(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(SETTINGS, "llm_provider", "openai")
    monkeypatch.setattr(SETTINGS, "openai_api_key", "sk-test")

    assert llm_provider_configured() is True


def test_get_chat_model_selects_groq_client_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(SETTINGS, "llm_provider", "groq")
    monkeypatch.setattr(SETTINGS, "groq_api_key", "gsk-test")
    monkeypatch.setattr(SETTINGS, "groq_model", "llama-3.3-70b-versatile")

    model = get_chat_model(temperature=0)

    assert model.__class__.__name__ == "ChatGroq"


def test_get_chat_model_selects_openai_client_when_configured(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(SETTINGS, "llm_provider", "openai")
    monkeypatch.setattr(SETTINGS, "openai_api_key", "sk-test")
    monkeypatch.setattr(SETTINGS, "openai_model", "gpt-4o-mini")

    model = get_chat_model(temperature=0)

    assert model.__class__.__name__ == "ChatOpenAI"
