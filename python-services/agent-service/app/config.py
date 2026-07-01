from __future__ import annotations

import os

from app.core.settings import SETTINGS


def configure_runtime_environment() -> None:
    os.environ["LANGSMITH_TRACING"] = "true" if SETTINGS.langsmith_tracing else "false"
    os.environ["LANGSMITH_ENDPOINT"] = SETTINGS.langsmith_endpoint
    os.environ["LANGSMITH_PROJECT"] = SETTINGS.langsmith_project

    if SETTINGS.langsmith_api_key:
        os.environ["LANGSMITH_API_KEY"] = SETTINGS.langsmith_api_key

    if SETTINGS.groq_api_key:
        os.environ["GROQ_API_KEY"] = SETTINGS.groq_api_key
    if SETTINGS.openai_api_key:
        os.environ["OPENAI_API_KEY"] = SETTINGS.openai_api_key
