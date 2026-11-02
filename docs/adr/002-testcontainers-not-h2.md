# ADR-002: Integration tests run on real PostgreSQL via Testcontainers

**Status:** Accepted (M0)

## Context
The schema relies on PostgreSQL features (`citext`, `pg_trgm`, GIN indexes, partial unique indexes, `SKIP LOCKED`, advisory locks) that H2 does not reproduce faithfully.

## Decision
All persistence and integration tests use `postgres:16-alpine` through Testcontainers with `@ServiceConnection`. H2 is not used.

## Consequences
Tests need a Docker daemon (available on GitHub-hosted runners). Container startup adds a few seconds per test run.
