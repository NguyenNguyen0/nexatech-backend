# Skill: Payment Index

## Purpose
Use this index for payment detail, payment by order, refunds, payment creation,
and payment statistics.

## Activation Conditions
Use when: request mentions payment, refund, invoice, transaction, VNPay, or
payment stats.
Do not use for: order cancellation unless the task also asks about payment.

## Steps
### Step 1 - Prefer exact lookup
Tool: `get_payment(payment_id)` or `get_payment_by_order(order_id)`
If no identifier exists: ask for payment ID or order ID.

### Step 2 - Mutate only with approval
Tool: `create_payment(order_id, method, idempotency_key)` or
`refund_payment(payment_id, idempotency_key)`
If `requiresApproval=true`: pause for HITL.

## Rollback
Refund and payment creation rollback is not automatic. Surface IDs and request
human review before compensating actions.

## Output Contract
Return JSON following `shared/output_contract.md`.
