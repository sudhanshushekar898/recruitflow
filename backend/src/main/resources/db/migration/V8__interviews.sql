-- interview module: scheduled interviews, who attends, and each interviewer's feedback.
CREATE TABLE interviews (
    id               uuid PRIMARY KEY,
    application_id   uuid          NOT NULL REFERENCES applications (id),
    round_no         integer       NOT NULL CHECK (round_no >= 1),
    type             varchar(20)   NOT NULL CHECK (type IN ('PHONE', 'VIDEO', 'ONSITE', 'TECHNICAL')),
    scheduled_start  timestamptz   NOT NULL,
    scheduled_end    timestamptz   NOT NULL,
    location_or_link varchar(300),
    status           varchar(20)   NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    notes            varchar(2000),
    version          bigint        NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_interviews_window CHECK (scheduled_end > scheduled_start),
    CONSTRAINT uq_interviews_round UNIQUE (application_id, round_no)
);
CREATE INDEX ix_interviews_application ON interviews (application_id);
CREATE INDEX ix_interviews_start ON interviews (scheduled_start);

CREATE TABLE interview_participants (
    interview_id uuid        NOT NULL REFERENCES interviews (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES users (id),
    role         varchar(10) NOT NULL CHECK (role IN ('LEAD', 'PANEL')),
    PRIMARY KEY (interview_id, user_id)
);
CREATE INDEX ix_interview_participants_user ON interview_participants (user_id);

CREATE TABLE interview_feedback (
    id             uuid PRIMARY KEY,
    interview_id   uuid        NOT NULL REFERENCES interviews (id) ON DELETE CASCADE,
    interviewer_id uuid        NOT NULL REFERENCES users (id),
    rating         integer     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    recommendation varchar(12) NOT NULL CHECK (recommendation IN ('STRONG_YES', 'YES', 'NO', 'STRONG_NO')),
    strengths      text,
    concerns       text,
    submitted_at   timestamptz NOT NULL,
    version        bigint      NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    -- One piece of feedback per interviewer per interview; the database decides races.
    CONSTRAINT uq_feedback_interviewer UNIQUE (interview_id, interviewer_id)
);
