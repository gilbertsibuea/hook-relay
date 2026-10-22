# HookRelay

Multi-tenant webhook delivery platform (Package A). Clients ingest events over REST; HookRelay stores them durably in PostgreSQL, relays work through Kafka, and delivers signed HTTP webhooks with retry, DLQ, and admin visibility.

> Status: **Milestone 1 in progress** — schema, tenant provisioning, and API key auth are available.

## Tech stack (pinned)

| Layer | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Maven Wrapper | generated locally in repo |
| PostgreSQL | `postgres:16.15-alpine` |
| Redis | `redis:7.4.11-alpine` |
| Kafka (KRaft) | `apache/kafka:4.1.2` |
| Prometheus | `prom/prometheus:v3.5.5` |
| Grafana | `grafana/grafana:11.6.16` |
| OpenTelemetry Collector | `otel/opentelemetry-collector-contrib:0.139.0` |
| Resilience4j | 2.4.0 |
| k6 (host CLI) | v1.3.0 |
| OpenTofu (host CLI) | 1.10.6 |

## Modules

- `common` — shared contracts/utilities
- `ingestion-api` — event ingestion and endpoint management
- `delivery-worker` — routing, delivery, retry/DLQ
- `admin-api` — GraphQL admin API

## Quick start (local infra)

```bash
cp .env.example .env
./mvnw clean verify
docker compose -f deploy/docker-compose.yml --env-file .env up -d
```

Or use Make:

```bash
make bootstrap
make verify
make infra-up
make provision-tenant NAME="Dev Tenant"
```

Provision prints the raw API key **once**. Authenticate with:

```http
X-API-Key: <raw-api-key>
GET /v1/me
```

There is no public HTTP endpoint for tenant provisioning (CLI only).

Infra health endpoints:

- PostgreSQL: `localhost:5433` (host port; container still listens on 5432)
- Redis: `localhost:6379`
- Kafka EXTERNAL listener: `localhost:29092`
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000

## Design decisions

- [ADR-001 Kafka KRaft](docs/adr/ADR-001-kafka.md)
- [ADR-003 Transactional outbox](docs/adr/ADR-003-transactional-outbox.md)
- [ADR-004 At-least-once delivery](docs/adr/ADR-004-at-least-once-delivery.md)

## Security notes

- `.env` is gitignored; use `.env.example` placeholders only.
- Do not commit API keys, signing secrets, or encryption keys.
- CI runs build/test plus Gitleaks secret scanning.

## Known limitations (current)

- Endpoint CRUD, event ingestion, delivery worker, GraphQL domain schema, and Terraform environments are not implemented yet.
- Application containers are not yet added to Compose.
- Benchmark numbers are intentionally absent until measured.
