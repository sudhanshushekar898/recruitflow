-- candidate module. owner_id references users(id) at the database level only; Java code holds a plain UUID.
CREATE TABLE candidates (
    id               uuid PRIMARY KEY,
    first_name       varchar(100) NOT NULL,
    last_name        varchar(100) NOT NULL,
    email            varchar(254) NOT NULL,          -- stored lower-case
    phone            varchar(30),
    current_title    varchar(150),
    current_company  varchar(150),
    location         varchar(150),
    years_experience integer CHECK (years_experience BETWEEN 0 AND 60),
    source           varchar(20)  NOT NULL CHECK (source IN ('LINKEDIN', 'REFERRAL', 'JOB_BOARD', 'AGENCY', 'OTHER')),
    summary          text,
    linkedin_url     varchar(300),
    status           varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DO_NOT_CONTACT', 'ANONYMISED')),
    owner_id         uuid         NOT NULL REFERENCES users (id),
    retention_until  timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    -- One lower-cased text blob that the keyword search matches against (see ADR-004).
    search_text      text GENERATED ALWAYS AS (lower(
                         first_name || ' ' || last_name || ' ' || email || ' ' ||
                         coalesce(current_title, '') || ' ' || coalesce(current_company, '') || ' ' ||
                         coalesce(location, ''))) STORED
);

-- Duplicate detection: one live candidate per email (anonymised records free the address).
CREATE UNIQUE INDEX uq_candidates_email_live ON candidates (lower(email)) WHERE status <> 'ANONYMISED';
CREATE INDEX ix_candidates_search_text_trgm ON candidates USING gin (search_text gin_trgm_ops);
CREATE INDEX ix_candidates_location_trgm ON candidates USING gin (lower(location) gin_trgm_ops);
CREATE INDEX ix_candidates_owner ON candidates (owner_id);
CREATE INDEX ix_candidates_status_created ON candidates (status, created_at DESC);
CREATE INDEX ix_candidates_retention ON candidates (retention_until);

CREATE TABLE candidate_skills (
    candidate_id uuid NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    skill_id     uuid NOT NULL REFERENCES skills (id),
    years        integer CHECK (years BETWEEN 0 AND 60),
    level        varchar(20) CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    PRIMARY KEY (candidate_id, skill_id)
);
CREATE INDEX ix_candidate_skills_skill ON candidate_skills (skill_id);

CREATE TABLE candidate_notes (
    id           uuid PRIMARY KEY,
    candidate_id uuid        NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    author_id    uuid        NOT NULL REFERENCES users (id),
    body         text        NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
    version      bigint      NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_candidate_notes_candidate ON candidate_notes (candidate_id, created_at DESC);

CREATE TABLE candidate_consents (
    candidate_id uuid PRIMARY KEY REFERENCES candidates (id) ON DELETE CASCADE,
    legal_basis  varchar(30)  NOT NULL CHECK (legal_basis IN ('CONSENT', 'LEGITIMATE_INTEREST')),
    given_at     timestamptz  NOT NULL,
    expires_at   timestamptz,
    evidence     varchar(300),
    withdrawn_at timestamptz,
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);
