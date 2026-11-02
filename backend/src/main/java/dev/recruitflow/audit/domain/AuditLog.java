package dev.recruitflow.audit.domain;

import dev.recruitflow.shared.persistence.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** One append-only audit record. The database rejects UPDATE and DELETE on this table. */
@Entity
@Table(name = "audit_log")
public class AuditLog implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID actorId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 40)
    private String entityType;

    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(length = 45)
    private String ip;

    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant occurredAt;

    @Transient
    private boolean isNew;

    protected AuditLog() {}

    public AuditLog(UUID actorId, String action, String entityType, UUID entityId,
                    Map<String, Object> details, String ip, String correlationId, Instant occurredAt) {
        this.id = Ids.newId();
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ip = ip;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
