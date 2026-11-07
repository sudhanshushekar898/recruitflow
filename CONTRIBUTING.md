# Contributing to RecruitFlow

These are the engineering rules every change follows. If a change needs to break one, record the decision as an ADR in [`docs/adr`](docs/adr) first.

## Architecture

1. **Modular monolith.** One deployable. There is one package per module under `dev.recruitflow`: `shared`, `identity`, `audit`, `skill`, `candidate`, `job`, `pipeline` and `interview`.
2. **Layers inside a module:** `api` → `service` → `domain` → `repository`/`infra`. Controllers never call repositories, and entities never leave the service layer.
3. **Between modules:**
   - Refer to other modules by ID only, never through JPA associations.
   - Call another module only through its public facade, which lives in the module's root package.
   - `analytics` is the only module allowed to read other modules' tables, and only read-only.
4. **Boundary tests:** ArchUnit tests enforce these rules ([`ArchitectureTest`](backend/src/test/java/dev/recruitflow/ArchitectureTest.java)). Never weaken them to make code compile.
5. **No new infrastructure without a reason.** No message broker, cache, search engine or extra service unless an ADR documents a measured need.

## Coding conventions

- Constructor injection only. Use Java records for DTOs and value objects.
- Request DTOs carry Bean Validation, and JSON is never bound to entities. Unknown JSON properties are rejected.
- `@Transactional` goes on service methods, with `readOnly = true` for queries. Never put it on controllers.
- IDs are UUIDv7 generated in the application. Timestamps are UTC `Instant`s, and money is `BigDecimal` plus a currency code.
- Enums are stored as strings and backed by database `CHECK` constraints.
- Errors are domain exceptions, which the global handler maps to RFC 7807 `problem+json` with a stable `code`.
- Sort fields are whitelisted per endpoint, and the maximum page size is 100.
- Comments explain *why*, not *what*.

## Database

- Schema changes go only through Flyway (`backend/src/main/resources/db/migration`). Never edit a migration that has already been applied.
- Hibernate runs with `ddl-auto=validate`. Constraints and indexes live in the database: unique pairs, `CHECK`s and foreign keys.
- Integration tests use Testcontainers PostgreSQL, never H2.

## Security and privacy

- Deny by default. Every endpoint has a role rule, plus an object-level check wherever data is owner- or participant-scoped. Every new endpoint ships with authorization tests.
- Never log or return passwords, tokens, cookies, `Authorization` headers, emails, phone numbers or candidate request bodies. Log IDs only.
- Candidate data is personal data. Keep fields to a minimum, record the legal basis, and honour retention.
- Use synthetic data only in development, tests, CI and demos. Never commit real personal data or secrets.

## Testing

- New behaviour comes with tests in the same change. The CI build enforces at least 80% line coverage.
- Test layers:
  - Unit tests: JUnit 5 and Mockito.
  - Integration tests: Testcontainers.
  - API tests with a role-by-ownership matrix.
  - Concurrency tests for races.
  - ArchUnit tests.
  - Frontend tests: Vitest, Testing Library and MSW.
- Test names describe behaviour, for example `rejectsTransitionFromHiredToNew`.

## API

- The base path is `/api/v1`. The OpenAPI spec is generated from code, and `frontend/src/types/api.ts` is generated from the spec (`npm run gen:api`). Never edit it by hand.
- Updates use `PATCH` with the entity `version` for optimistic locking, and a stale version returns `409 STALE_VERSION`. State changes use action sub-resources such as `/status` or `/archive`.

## Workflow

- Use short-lived branches with [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `chore:`), merged by pull request once CI is green.
- **Definition of done:**
  - Tests are green.
  - A migration is included if the schema changed.
  - Every new endpoint has authorization tests.
  - Sensitive actions are audited.
  - No personal data appears in the logs.
  - The docs and ADRs are updated.

## Commands

```bash
# backend (from backend/)
./mvnw verify                 # unit + integration + architecture rules + coverage gate
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# frontend (from frontend/)
npm ci && npm run dev
npm run lint && npm run typecheck && npm test && npm run build
npm run gen:api               # regenerate API types from the running backend

# whole stack
docker compose --profile full up -d --build
```
