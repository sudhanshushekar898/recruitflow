-- pipeline module: one application per candidate and job, plus its append-only status history.
CREATE TABLE applications (
    id                uuid PRIMARY KEY,
    candidate_id      uuid        NOT NULL REFERENCES candidates (id),
    job_id            uuid        NOT NULL REFERENCES jobs (id),
    status            varchar(20) NOT NULL DEFAULT 'NEW'
                      CHECK (status IN ('NEW', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    rejection_reason  varchar(30),
    source            varchar(20) NOT NULL CHECK (source IN ('LINKEDIN', 'REFERRAL', 'JOB_BOARD', 'AGENCY', 'OTHER')),
    owner_id          uuid        NOT NULL REFERENCES users (id),
    applied_at        timestamptz NOT NULL,
    status_changed_at timestamptz NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    -- A candidate applies to a job once. The database decides races, not application code.
    CONSTRAINT uq_applications_candidate_job UNIQUE (candidate_id, job_id),
    CONSTRAINT ck_applications_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
CREATE INDEX ix_applications_job_status ON applications (job_id, status);
CREATE INDEX ix_applications_candidate ON applications (candidate_id);
CREATE INDEX ix_applications_owner_status ON applications (owner_id, status);

CREATE TABLE application_status_history (
    id             uuid PRIMARY KEY,
    application_id uuid        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status    varchar(20),
    to_status      varchar(20) NOT NULL,
    changed_by     uuid REFERENCES users (id),
    changed_at     timestamptz NOT NULL,
    comment        varchar(1000)
);
CREATE INDEX ix_status_history_application ON application_status_history (application_id, changed_at);
CREATE INDEX ix_status_history_to_status ON application_status_history (to_status, changed_at);
