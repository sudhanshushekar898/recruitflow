-- job module: client companies, job postings and the skills each job asks for.
CREATE TABLE clients (
    id            uuid PRIMARY KEY,
    name          varchar(150) NOT NULL,
    industry      varchar(100),
    contact_name  varchar(150),
    contact_email varchar(254),
    active        boolean      NOT NULL DEFAULT true,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_clients_name_lower ON clients (lower(name));

CREATE TABLE jobs (
    id              uuid PRIMARY KEY,
    client_id       uuid         NOT NULL REFERENCES clients (id),
    title           varchar(150) NOT NULL,
    description     text,
    location        varchar(150),
    employment_type varchar(20)  NOT NULL CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT')),
    salary_min      numeric(12, 2) CHECK (salary_min >= 0),
    salary_max      numeric(12, 2) CHECK (salary_max >= 0),
    currency        varchar(3),
    openings        integer      NOT NULL DEFAULT 1 CHECK (openings >= 1),
    status          varchar(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'ON_HOLD', 'CLOSED')),
    owner_id        uuid         NOT NULL REFERENCES users (id),
    opened_at       timestamptz,
    closed_at       timestamptz,
    version         bigint       NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_jobs_salary_range CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    search_text     text GENERATED ALWAYS AS (lower(title || ' ' || coalesce(location, ''))) STORED
);
CREATE INDEX ix_jobs_status_client ON jobs (status, client_id);
CREATE INDEX ix_jobs_owner ON jobs (owner_id);
CREATE INDEX ix_jobs_client ON jobs (client_id);
CREATE INDEX ix_jobs_search_text_trgm ON jobs USING gin (search_text gin_trgm_ops);

CREATE TABLE job_skills (
    job_id     uuid        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    skill_id   uuid        NOT NULL REFERENCES skills (id),
    importance varchar(15) NOT NULL CHECK (importance IN ('REQUIRED', 'NICE_TO_HAVE')),
    min_years  integer CHECK (min_years BETWEEN 0 AND 60),
    weight     integer     NOT NULL DEFAULT 3 CHECK (weight BETWEEN 1 AND 5),
    PRIMARY KEY (job_id, skill_id)
);
CREATE INDEX ix_job_skills_skill ON job_skills (skill_id);
