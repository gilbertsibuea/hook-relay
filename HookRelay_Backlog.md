# HookRelay Backlog

Track progress by flipping `[ ]` ↔ `[x]`.  
Source of truth for scope remains `HookRelay_Final_Project_Spec.md`.

**Last updated:** 2026-10-22  
**Current milestone focus:** Milestone 1 — secure ingestion (endpoints done → SSRF / event ingestion next)

| Status | Count |
|---|---:|
| Done | 8 |
| Remaining P0 | 12 |
| Remaining P1 | 19 |
| Stretch P2 | 1 |

---

## Legend

| Mark | Meaning |
|---|---|
| `[x]` | Done — acceptance criteria met for current scope |
| `[ ]` | Not done |
| **P0** | Core MVP blocker |
| **P1** | Complete Package A blocker |
| **P2** | Stretch only |

---

## Foundation — Milestone 0

- [x] **HR-001** · P0 · ~3h · Create Maven multi-module repository and wrapper  
  - **Deps:** None  
  - **Done:** Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.11, modules `common` / `ingestion-api` / `delivery-worker` / `admin-api`  
  - **AC:** Fresh clone builds with Java 21 and pinned Spring Boot version

- [x] **HR-002** · P0 · ~4h · Pin Docker images and create local Compose stack  
  - **Deps:** HR-001  
  - **Done:** `deploy/docker-compose.yml` with Postgres 16.15, Redis 7.4.11, Kafka KRaft 4.1.2 (+ Prometheus/Grafana/OTel); health checks green; host Postgres on `5433`  
  - **AC:** PostgreSQL, Redis, Kafka KRaft start with health checks

- [x] **HR-003** · P0 · ~3h · Setup GitHub Actions build and test  
  - **Deps:** HR-001  
  - **Done:** `.github/workflows/ci.yml` runs `./mvnw clean verify`, uploads JaCoCo, validates Compose, runs Gitleaks  
  - **AC:** CI executes clean verify and uploads test reports  
  - **Note:** Full dependency-scan gating remains **HR-035**

- [x] **HR-004** · P0 · ~3h · Create initial architecture diagram and ADRs  
  - **Deps:** HR-001  
  - **Done:** `docs/architecture/system-context.md`, ADR-001 (Kafka), ADR-003 (outbox), ADR-004 (at-least-once), threat-model draft  
  - **AC:** Kafka, outbox, and at-least-once decisions documented

---

## Data

- [x] **HR-005** · P0 · ~5h · Create Flyway schema for tenants, keys, endpoints, events, idempotency, outbox  
  - **Deps:** HR-001  
  - **Done:** `V1__core_domain_schema.sql` with tenants, api_keys, endpoints, endpoint_subscriptions, events, idempotency_keys, outbox; unique constraints + integration tests  
  - **AC:** Schema migrates from empty database and has required unique constraints

---

## Auth

- [x] **HR-006** · P0 · ~2h · Implement tenant provisioning command/seed  
  - **Deps:** HR-005  
  - **Done:** CLI `provision-tenant` + `make provision-tenant` / `scripts/provision-tenant.sh`; no public HTTP provision endpoint; raw key printed once to stdout  
  - **AC:** Development tenant can be provisioned without a public unauthenticated endpoint

- [x] **HR-007** · P0 · ~5h · Implement API key generation, hashing, and authentication  
  - **Deps:** HR-005, HR-006  
  - **Done:** `hr_live_` key gen, SHA-256(+pepper) hash storage, `X-API-Key` filter, `GET /v1/me`, revoked/invalid → 401  
  - **AC:** Valid key resolves tenant; raw key is never persisted

---

## Endpoint

- [x] **HR-008** · P0 · ~5h · Implement endpoint create/list/update/deactivate  
  - **Deps:** HR-007  
  - **Done:** REST CRUD under `/v1/endpoints`; AES-GCM encrypted signing secret shown once on create; list/get/patch omit secret; soft-delete via DELETE; tenant isolation tests  
  - **AC:** CRUD is tenant-scoped and never returns stored signing secret

---

## Security

- [ ] **HR-009** · P0 · ~5h · Implement URL validation and SSRF checks baseline  
  - **Deps:** HR-008  
  - **AC:** Loopback, private, link-local, metadata, and invalid schemes are rejected

- [ ] **HR-024** · P1 · ~6h · Implement API key rotation/revocation and encrypted endpoint secret  
  - **Deps:** HR-007, HR-008  
  - **AC:** Revoked key is rejected; signing secret is encrypted at rest

- [ ] **HR-031** · P1 · ~5h · Add tenant isolation, SSRF, and secret redaction tests  
  - **Deps:** HR-009, HR-024, HR-025  
  - **AC:** Security regression suite runs in CI

---

## Ingestion

- [ ] **HR-010** · P0 · ~3h · Implement event request validation and size limit  
  - **Deps:** HR-007  
  - **AC:** Valid JSON accepted; malformed/oversized requests receive proper 4xx

- [ ] **HR-011** · P0 · ~7h · Implement transactional idempotent event ingestion  
  - **Deps:** HR-005, HR-007, HR-010  
  - **AC:** Same key + same payload → same event ID; mismatched payload → 409

---

## Outbox

- [ ] **HR-012** · P0 · ~6h · Implement outbox relay to Kafka  
  - **Deps:** HR-002, HR-011  
  - **AC:** Pending records publish; relay resumes safely after restart

---

## Routing

- [ ] **HR-013** · P0 · ~6h · Implement event-accepted consumer and endpoint fan-out  
  - **Deps:** HR-008, HR-012  
  - **AC:** Only subscribed endpoints receive one delivery row per event–endpoint pair

---

## Delivery

- [ ] **HR-014** · P0 · ~4h · Create deliveries, attempts, and delivery-command outbox schema  
  - **Deps:** HR-005, HR-013  
  - **AC:** Delivery persistence includes status and unique event–endpoint constraint

- [ ] **HR-015** · P0 · ~7h · Implement scheduler, safe claims, leases, and recovery  
  - **Deps:** HR-014  
  - **AC:** Concurrent workers do not actively claim the same delivery; stale leases recover

- [ ] **HR-016** · P0 · ~4h · Implement outbound HTTP client and timeout controls  
  - **Deps:** HR-015  
  - **AC:** Timeouts and response cap enforced; redirects disabled

---

## Signature / HMAC

- [ ] **HR-017** · P0 · ~5h · Implement HMAC signature generation and receiver example  
  - **Deps:** HR-016  
  - **AC:** Receiver validates raw-body signature and timestamp; tampering fails

---

## Retry / DLQ

- [ ] **HR-018** · P0 · ~6h · Implement retry classification, backoff, jitter, and Retry-After  
  - **Deps:** HR-015, HR-016  
  - **AC:** Transient errors schedule durable retries without sleeping worker threads

- [ ] **HR-019** · P0 · ~5h · Implement DLQ state and manual replay  
  - **Deps:** HR-018  
  - **AC:** Max attempts → DLQ; replay preserves prior attempts

---

## Rate limit & resilience

- [ ] **HR-021** · P1 · ~5h · Implement Redis token bucket per tenant  
  - **Deps:** HR-002, HR-007  
  - **AC:** Concurrent over-limit requests receive 429 and Retry-After

- [ ] **HR-022** · P1 · ~8h · Implement circuit breaker with shared Redis coordination  
  - **Deps:** HR-002, HR-015  
  - **AC:** Repeated endpoint failures open breaker; half-open probes are limited

- [ ] **HR-023** · P1 · ~5h · Implement per-endpoint bulkhead/concurrency isolation  
  - **Deps:** HR-016, HR-022  
  - **AC:** Slow endpoint does not exhaust capacity for a healthy endpoint

---

## Admin (GraphQL)

- [ ] **HR-025** · P1 · ~6h · Implement GraphQL event, delivery, and endpoint queries  
  - **Deps:** HR-014, HR-008  
  - **Partial:** Bootstrap GraphQL `health` query only  
  - **AC:** Queries paginate; all resources are tenant-scoped

- [ ] **HR-026** · P1 · ~5h · Implement GraphQL replay, pause, and resume mutations  
  - **Deps:** HR-019, HR-025  
  - **AC:** Mutation validates ownership/state and records audit information

---

## Retention

- [ ] **HR-027** · P1 · ~4h · Implement configurable retention job  
  - **Deps:** HR-005, HR-014  
  - **AC:** Expired payload data cleaned per policy without unintended audit deletion

---

## Observability

- [ ] **HR-028** · P1 · ~5h · Add Micrometer metrics and health/readiness  
  - **Deps:** HR-001, HR-012, HR-019  
  - **Partial:** Actuator + Prometheus registry wired; domain metrics not yet defined  
  - **AC:** Prometheus can scrape key ingestion, outbox, queue, and delivery metrics

- [ ] **HR-029** · P1 · ~4h · Setup Prometheus and Grafana dashboards  
  - **Deps:** HR-028  
  - **Partial:** Compose services + datasource provisioning exist; dashboards not built  
  - **AC:** Dashboards show latency, errors, backlog, retries, DLQ, and resources

- [ ] **HR-030** · P1 · ~6h · Add structured logs, correlation IDs, and trace propagation  
  - **Deps:** HR-012, HR-016, HR-028  
  - **Partial:** OTel Collector present in Compose; app instrumentation not done  
  - **AC:** Trace can be followed across ingestion → Kafka → delivery worker

---

## Testing

- [ ] **HR-020** · P0 · ~8h · Add unit and integration tests for core lifecycle  
  - **Deps:** HR-011, HR-012, HR-019  
  - **Partial:** Bootstrap smoke/context tests only  
  - **AC:** Tests cover idempotency, outbox, fan-out, signature, retry, and DLQ

- [ ] **HR-034** · P1 · ~6h · Add receiver outage and worker kill tests  
  - **Deps:** HR-020, HR-028  
  - **AC:** No accepted event disappears from durable accounting in defined scenarios

---

## Load test

- [ ] **HR-032** · P1 · ~4h · Create k6 ingestion benchmark  
  - **Deps:** HR-011, HR-028  
  - **Partial:** k6 CLI installed (`~/bin/k6` v1.3.0); scenarios not written  
  - **AC:** Raw results include latency, throughput, errors, and environment details

- [ ] **HR-033** · P1 · ~5h · Run worker scaling benchmark 1 vs 3 instances  
  - **Deps:** HR-019, HR-032  
  - **AC:** Same workload executed; results reported honestly

---

## CI / Infra / Docs / Ops / Release

- [ ] **HR-035** · P1 · ~4h · Add dependency scan, secret scan, and coverage report  
  - **Deps:** HR-003, HR-020  
  - **Partial:** Gitleaks + JaCoCo upload already in CI; dependency scan / gating policy pending  
  - **AC:** Pipeline reports findings and enforces documented gating policy

- [ ] **HR-036** · P1 · ~8h · Implement Terraform / OpenTofu dev environment  
  - **Deps:** HR-002, HR-028  
  - **Partial:** OpenTofu 1.10.6 installed; `infra/terraform` placeholder only  
  - **AC:** `fmt`, `validate`, and `plan` pass with documented provider/configuration

- [ ] **HR-037** · P1 · ~5h · Write English README and API/signature documentation  
  - **Deps:** HR-017, HR-025, HR-032  
  - **Partial:** Bootstrap README exists; full API/signature docs pending  
  - **AC:** Fresh clone quick start and examples are verified

- [ ] **HR-038** · P1 · ~3h · Write outage and DLQ runbooks  
  - **Deps:** HR-019, HR-029  
  - **AC:** Runbooks give actionable diagnosis and recovery steps

- [ ] **HR-039** · P1 · ~5h · Run final acceptance matrix and record evidence  
  - **Deps:** HR-031, HR-033, HR-034, HR-037  
  - **AC:** All P0/P1 criteria pass or have explicit exception notes

---

## Stretch

- [ ] **HR-040** · P2 · ~6h · Create receiver SDK examples in another language  
  - **Deps:** HR-037  
  - **AC:** Example verifies signature and deduplicates event ID

---

## Suggested next slice (Milestone 1)

1. **HR-009** — SSRF URL validation baseline  
2. **HR-010** / **HR-011** — idempotent ingestion + `202` contract  
3. **HR-012** — outbox relay to Kafka  
4. **HR-013** — fan-out routing 

---

## How to update

1. Mark a task `[x]` when its acceptance criteria are demonstrable.  
2. Add a short **Done:** or **Partial:** note under the item.  
3. Bump the status counts and “Last updated” date at the top.  
4. Do not start cosmetic P1 work before P0 can be proven with tests and an end-to-end demo.
