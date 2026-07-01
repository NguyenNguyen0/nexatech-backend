from __future__ import annotations

from typing import Any

from app.core.settings import SETTINGS


def llm_provider_configured() -> bool:
    """Return True when the active LLM provider has an API key configured."""
    if SETTINGS.llm_provider == "openai":
        return bool(SETTINGS.openai_api_key)
    return bool(SETTINGS.groq_api_key)


def get_chat_model(*, temperature: float) -> Any:
    """Build the chat model for the configured LLM provider.

    Both providers take their API key directly on the client constructor —
    no provider-specific environment variables are required at call time.
    """
    if SETTINGS.llm_provider == "openai":
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            model=SETTINGS.openai_model,
            api_key=SETTINGS.openai_api_key,
            temperature=temperature,
        )

    from langchain_groq import ChatGroq

    return ChatGroq(
        model=SETTINGS.groq_model,
        api_key=SETTINGS.groq_api_key,
        temperature=temperature,
    )
