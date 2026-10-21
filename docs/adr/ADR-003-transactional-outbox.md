# ADR-003: Transactional outbox for durable publish

- **Status:** Accepted
- **Date:** 2026-10-22
- **Deciders:** HookRelay project baseline

## Context

After `POST /v1/events`, HookRelay must guarantee that an HTTP `202 Accepted` means the event is durable. Publishing directly to Kafka inside the request path can leave a gap where the database commits but Kafka publish fails, or the reverse.

## Decision

Persist event acceptance, idempotency records, and an outbox row in one PostgreSQL transaction. A separate outbox relay publishes pending rows to Kafka and records publish success afterward.

## Alternatives

1. **Dual write (DB then Kafka in the request thread)** — simpler code path; can acknowledge work that never reaches Kafka.
2. **Kafka transactions spanning DB** — not a practical substitute for application state in PostgreSQL.
3. **Change-data-capture only** — powerful, but adds connector operational burden beyond the MVP scope.

## Consequences

- `202` is issued only after the DB transaction commits, including the outbox record.
- Relay crashes can republish; consumers must tolerate duplicates.
- Outbox lag and oldest pending age become first-class operational metrics.
- Extra relay/scheduler components are required in the modular monolith.
