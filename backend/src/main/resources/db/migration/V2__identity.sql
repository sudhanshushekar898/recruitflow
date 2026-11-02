-- identity module. Emails are stored lower-cased by the application; uniqueness is enforced case-insensitively.
CREATE TABLE users (
    id                   uuid PRIMARY KEY,
    email                varchar(254) NOT NULL,
    password_hash        varchar(100),           -- NULL until an invited user accepts the invite
    first_name           varchar(100) NOT NULL,
    last_name            varchar(100) NOT NULL,
    enabled              boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT false,
    failed_login_count   integer      NOT NULL DEFAULT 0,
    locked_until         timestamptz,
    last_login_at        timestamptz,
    version              bigint       NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE user_roles (
    user_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    varchar(30) NOT NULL CHECK (role IN ('ADMIN', 'RECRUITER', 'INTERVIEWER')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64)  NOT NULL,           -- SHA-256 hex of the opaque token; the raw token is never stored
    family_id   uuid         NOT NULL,           -- all rotations of one login share a family (reuse detection)
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    user_agent  varchar(255),
    ip          varchar(45),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE one_time_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    varchar(20) NOT NULL CHECK (purpose IN ('INVITE', 'PASSWORD_RESET')),
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    version    bigint      NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_one_time_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_one_time_tokens_user ON one_time_tokens (user_id, purpose);
