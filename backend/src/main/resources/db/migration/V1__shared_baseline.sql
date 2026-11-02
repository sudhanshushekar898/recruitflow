-- Extensions required by later modules (case-insensitive email, trigram search).
-- Both are available on Amazon RDS for PostgreSQL.
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
