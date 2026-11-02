# ADR-004: Candidate keyword search uses a generated text column with a trigram index

**Status:** Accepted (M3). Refines the search approach in ARCHITECTURE.md section 10.

## Context
Recruiters search candidates by typing a few words. The first design proposed a `tsvector` column. Full-text search stems and ranks words, but candidate search is mostly names, emails, titles and places, where people type fragments ("patel", "lon", "@acme") and expect substring matches. Stemming does not help and hides fragment matches.

## Decision
- `candidates.search_text` is a **stored generated column**: lower-cased first name, last name, email, title, company and location in one string, maintained by PostgreSQL itself so it can never drift from the data.
- A **`pg_trgm` GIN index** serves `LIKE '%word%'`. A query is split on whitespace and every word must match (AND).
- Skills are not in the text blob. They use the `skillId` filter with an `EXISTS` subquery per skill, so "must have all of these skills" is exact and uses the catalog (with aliases such as k8s) instead of fuzzy text.
- User input is escaped so `%` and `_` search literally.
- Guardrail: `CandidateSearchPerformanceIT` seeds 100,000 candidates, asserts the planner uses the trigram index, and requires median latency under 300 ms.

## Alternatives
- `tsvector` + `websearch_to_tsquery`: better for long free text (resume content, summaries). It stays the plan for M7 when resume text exists, as a separate column.
- Elasticsearch/OpenSearch: not justified at this scale (ADR-017).

## Consequences
- Matching is substring-based, not typo-tolerant or ranked. Ranking can be added with `similarity()` if needed.
- Adding a searched field means a migration that changes the generated column.
