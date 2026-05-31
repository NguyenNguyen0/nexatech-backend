# Skill: Orchestrator Routing

## Purpose
Choose the correct MCP domain before exposing business tools to an agent.

## Activation Conditions
Use when: user asks for a task but the target tool is not already obvious.
Do not use for: direct tool invocation where the user names an exact tool.

## Steps
### Step 1 - Detect domain signals
Tool: `route_to_mcp_domain(intent)`
If result is `single_domain`: use the returned MCP endpoint.
If result is `multi_domain`: load `workflow_skill.md`.

### Step 2 - Confirm business boundary
Tool: `get_mcp_domain(domain)`
If the domain is unknown: ask for clarification.

## Rollback
No rollback. Routing does not mutate state.

## Output Contract
```json
{
  "status": "success|needs_clarification",
  "routeType": "single_domain|multi_domain|unknown",
  "domains": []
}
```
