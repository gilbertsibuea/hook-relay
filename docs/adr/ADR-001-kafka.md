# ADR-001: Kafka in KRaft mode as the event transport

- **Status:** Accepted
- **Date:** 2026-10-22
- **Deciders:** HookRelay project baseline

## Context

HookRelay must move accepted events and delivery commands asynchronously between ingestion, routing, and delivery workers. The platform needs consumer groups, replay-friendly buffers, and an industry-standard event backbone suitable for a reliability-focused portfolio project.

## Decision

Use Apache Kafka in KRaft mode (no ZooKeeper) as the transport and work buffer for `event.accepted` and delivery command topics.

## Alternatives

1. **RabbitMQ / other AMQP brokers** — simpler local ops, weaker fit for the stated event-driven learning goals.
2. **PostgreSQL `LISTEN/NOTIFY` or polling only** — fewer moving parts, but weaker isolation between producers and multiple workers at scale.
3. **Kafka with ZooKeeper** — obsolete operational model relative to current Kafka baselines.

## Consequences

- Local Compose must run a pinned Kafka KRaft image with health checks.
- Consumers must be idempotent because Kafka provides at-least-once delivery to consumers.
- Kafka is not the source of truth for business state; PostgreSQL remains authoritative.
- Operational complexity is higher than a single-process queue, which is an accepted trade-off for the project goals.
