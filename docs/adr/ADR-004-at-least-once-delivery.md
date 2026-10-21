# ADR-004: At-least-once delivery semantics

- **Status:** Accepted
- **Date:** 2026-10-22
- **Deciders:** HookRelay project baseline

## Context

Outbound HTTP webhooks cannot participate in the same atomic transaction as PostgreSQL state updates. A worker may successfully deliver a webhook and crash before persisting the success outcome, causing a later retry.

## Decision

HookRelay provides **at-least-once** delivery. Receivers must deduplicate using the stable event ID. The platform will not claim exactly-once end-to-end delivery.

## Alternatives

1. **Exactly-once end-to-end** — not achievable for arbitrary external HTTP receivers without receiver cooperation and distributed consensus beyond scope.
2. **At-most-once** — simpler, but silently drops events under failure; incompatible with durability goals.
3. **Inbox table on the receiver managed by HookRelay** — would require controlling every receiver codebase.

## Consequences

- Webhook envelope includes a stable event ID and HMAC signature.
- Documentation and receiver examples must explain deduplication.
- Crash-window tests may observe duplicate deliveries; that is expected evidence, not a defect by itself.
- Retry, DLQ, and replay keep durable state in PostgreSQL rather than blocking threads.
