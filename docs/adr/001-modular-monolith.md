# ADR-001: Modular monolith with ArchUnit-enforced boundaries

**Status:** Accepted (M0)

## Context
About 10 users and a small team. Distributed services would add network failure modes and operational cost with no benefit.

## Decision
One Spring Boot deployable, one PostgreSQL database. Each module is a top-level package under `dev.recruitflow` with a small public facade. Modules reference each other by ID only and communicate via facades and domain events. `ArchitectureTest` enforces: no module cycles, `shared` depends on no module, controllers do not use repositories, no field injection.

## Alternatives
- Microservices: rejected (cost without benefit at this scale).
- Plain layered monolith: rejected (boundaries erode).
- Multi-module Maven / Spring Modulith: possible later; heavier than needed now.

## Consequences
Modules can be extracted later along ID-only seams. The ArchUnit tests must never be weakened to make code compile.
