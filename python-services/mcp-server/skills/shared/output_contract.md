# Skill: Shared Output Contract

## Purpose
Use this skill to format sub-agent results for the orchestrator. The output must
be machine-readable JSON, not prose.

## Activation Conditions
Use when: returning from any domain workflow, multi-step task, or meta routing
decision.
Do not use for: direct final user-facing text generation.

## Steps
### Step 1 - Return normalized status
Tool: none
Map tool envelopes into `status`, `data`, `errors`, and `nextActions`.

### Step 2 - Include trace data
Tool: none
Preserve `tag`, `correlationId`, `selectedTool`, and relevant IDs.

## Rollback
No rollback. This skill only formats output.

## Output Contract
```json
{
  "status": "success|partial|failed|needs_approval",
  "domain": "inventory|product|order|payment|promotion|news|audit|meta",
  "data": {},
  "errors": [],
  "nextActions": [],
  "correlationId": "optional"
}
```
