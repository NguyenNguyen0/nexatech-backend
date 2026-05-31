# Skill: Orchestrator Index

## Purpose
Use this index before choosing a domain MCP or before executing a workflow that
spans multiple domains.

## Activation Conditions
Use when: request mentions several business areas, has ambiguous intent, needs
rollback, or includes phrases like order plus payment, order plus stock, catalog
plus promotion, or trace plus audit.
Do not use for: a clear single-domain read-only lookup.

## Steps
### Step 1 - Route the intent
Tool: `route_to_mcp_domain(intent)`
If no domain matches: ask a short clarification question.

### Step 2 - Load the domain skill
Tool: `get_skill(domain, skill_name)`
If unsure: call `get_skill(domain)` for the domain index.

## Rollback
Follow `shared/error_handling.md`.

## Output Contract
Return JSON following `shared/output_contract.md`.
