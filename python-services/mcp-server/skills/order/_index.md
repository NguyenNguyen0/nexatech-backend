# Skill: Order Index

## Purpose
Use this index for order detail, order history, cancellation, purchase checks,
stats, and traces.

## Activation Conditions
Use when: request mentions order, purchased, cancellation, order stats, or order
trace.
Do not use for: payment-only lookup without an order ID.

## Steps
### Step 1 - Pick an order skill
Tool: `get_skill("order", skill_name)`
Choose `process_order_skill` for cross-domain order workflows.

## Rollback
Use `shared/error_handling.md`.

## Output Contract
Return JSON following `shared/output_contract.md`.
