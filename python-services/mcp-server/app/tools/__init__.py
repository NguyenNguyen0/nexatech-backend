"""MCP tool package.

Tool modules are imported selectively by ``app.server`` based on
``MCP_SERVER_PROFILE``. Avoid importing domain modules here, otherwise every
profile would register every tool.
"""

from __future__ import annotations
