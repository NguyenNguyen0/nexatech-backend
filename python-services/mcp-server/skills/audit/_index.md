# Skill: Audit Index

## Purpose
Use this index for audit search, audit detail, facets, and trace lookup.

## Activation Conditions
Use when: request mentions audit, log, trace, correlation ID, source service,
actor, action, or incident investigation.
Do not use for: normal product/order reads unless tracing is requested.

## Steps
### Step 1 - Search or facet first
Tool: `search_audit_logs(params)` or `get_audit_facets()`
If filters are unknown: fetch facets before search.

### Step 2 - Trace or inspect detail
Tool: `get_audit_trace(lookup_id)` or `get_audit_entry(entry_id)`
If not found: return structured not-found error.

## Rollback
No rollback. This skill is read-only.

## Output Contract
Return JSON following `shared/output_contract.md`.
