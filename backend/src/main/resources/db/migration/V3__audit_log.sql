-- audit module: append-only. details holds ids and field names only, never PII values.
CREATE TABLE audit_log (
    id             uuid PRIMARY KEY,
    actor_id       uuid,                      -- NULL for system or unauthenticated actions
    action         varchar(60)  NOT NULL,
    entity_type    varchar(40),
    entity_id      uuid,
    details        jsonb,
    ip             varchar(45),
    correlation_id varchar(64),
    occurred_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, occurred_at);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, occurred_at);
CREATE INDEX ix_audit_action ON audit_log (action, occurred_at);

CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
