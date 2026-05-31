# Skill: Process Order

## Purpose
Coordinate order workflows that may involve inventory reservation and payment.

## Activation Conditions
Use when: checkout, cancel order, continue payment, verify purchase, or trace an
order flow is requested.
Do not use for: product browsing.

## Steps
### Step 1 - Read order context
Tool: `get_order(order_id)` or `get_my_orders()`
If no order ID exists: ask for the order ID unless current-user history is enough.

### Step 2 - Handle payment continuation if requested
Tool: `continue_order_payment(order_id, idempotency_key)`
If result requires approval: pause for HITL.

### Step 3 - Handle cancellation if requested
Tool: `cancel_order(order_id, idempotency_key)`
If cancellation fails: return tag, message, and correlation ID.

## Rollback
Order cancellation rollback is not automatic. If inventory/payment side effects
occur, request human review before compensating actions.

## Output Contract
Return JSON following `shared/output_contract.md`.
