# RecruitFlow

Recruitment operations and applicant tracking platform (modular monolith: Java 21, Spring Boot, PostgreSQL, React + TypeScript, AWS).

- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Decisions: [docs/adr](docs/adr)
- Contributor and AI-assistant rules: [CLAUDE.md](CLAUDE.md)

## Run locally (M0)

```bash
docker compose up -d            # postgres, mailpit (S3-compatible storage arrives in M7)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Health: http://localhost:8080/actuator/health/readiness · API docs: http://localhost:8080/swagger-ui.html · Mail UI: http://localhost:8025

## Test

```bash
cd backend && mvn -B verify     # needs Docker running (Testcontainers)
```

Status: M0 foundation in progress. See the status log in CLAUDE.md.
