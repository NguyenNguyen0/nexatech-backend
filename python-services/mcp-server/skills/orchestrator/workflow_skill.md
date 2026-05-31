# Skill: Orchestrator Workflow

## Purpose
Coordinate cross-domain workflows while keeping side effects and compensating
actions explicit.

## Activation Conditions
Use when: a request needs inventory plus order, order plus payment, product plus
promotion, or audit after a failure.
Do not use for: one read-only lookup.

## Steps
### Step 1 - Load domain skills
Tool: `get_skill(domain)`
If failure: stop and report missing skill.

### Step 2 - Execute read-only checks first
Tool: domain read tools
If a read check fails: stop before mutating anything.

### Step 3 - Execute side effects with approval
Tool: domain write tools
If a tool result has `requiresApproval=true`: interrupt for HITL before execution.

## Rollback
Use reverse-order compensating actions. For stock reservation failure, call
`release_stock` only when a reservation or reserved item list is known.

## Output Contract
Return JSON following `shared/output_contract.md` with a `rollbackActions` list.
