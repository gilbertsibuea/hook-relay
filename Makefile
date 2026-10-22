.PHONY: help bootstrap test verify infra-up infra-down infra-ps lint provision-tenant

help:
	@echo "HookRelay local targets:"
	@echo "  make bootstrap         - copy .env.example to .env if missing"
	@echo "  make test              - run unit/integration tests"
	@echo "  make verify            - full Maven verify"
	@echo "  make infra-up          - start PostgreSQL, Redis, Kafka, observability stack"
	@echo "  make infra-down        - stop infra stack"
	@echo "  make infra-ps          - show infra container status"
	@echo "  make provision-tenant  - create a development tenant + API key (NAME=\"Dev Tenant\")"

bootstrap:
	@test -f .env || cp .env.example .env
	@echo ".env ready"

test:
	./mvnw -B test

verify:
	./mvnw -B clean verify

infra-up: bootstrap
	docker compose -f deploy/docker-compose.yml --env-file .env up -d

infra-down:
	docker compose -f deploy/docker-compose.yml --env-file .env down

infra-ps:
	docker compose -f deploy/docker-compose.yml ps

lint:
	./mvnw -B -DskipTests compile

provision-tenant: bootstrap
	./scripts/provision-tenant.sh "$(or $(NAME),Dev Tenant)"
