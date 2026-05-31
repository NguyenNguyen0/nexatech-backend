# Skill: Shared Error Handling

## Purpose
Use this skill whenever a workflow spans more than one tool or any tool can
mutate state. It keeps failures inspectable and rollback decisions explicit.

## Activation Conditions
Use when: tool result has `success=false`, `ok=false`, `error`, `tag`,
`statusCode`, timeout, partial success, or rollback metadata.
Do not use for: a single read-only lookup that already returned usable data.

## Steps
### Step 1 - Inspect the tool result
Tool: none
If failure: read `tag`, `message`, `correlationId`, `error.details`,
`error.rollbackNeeded`, and `error.retryAllowed`.

### Step 2 - Decide retry versus stop
Tool: none
If `retryAllowed=false`: stop, do not call the same mutating tool again.
If `statusCode` is 429, 502, 503, or 504 and the tool is read-only: retry once.

### Step 3 - Roll back side effects
Tool: domain-specific compensating tool
If a reservation exists: call `release_stock`.
If a payment was created but order flow failed: surface payment/order IDs and
ask for human review before refund or cancellation.

## Rollback
Run compensating actions in reverse order of side effects. Never invent IDs; use
IDs returned by previous tool results or ask the user to provide them.

## Output Contract
```json
{
  "status": "failed|rolled_back|needs_human_review",
  "tag": "SERVICE-0000",
  "message": "short explanation",
  "correlationId": "optional",
  "rollbackActions": []
}
```
