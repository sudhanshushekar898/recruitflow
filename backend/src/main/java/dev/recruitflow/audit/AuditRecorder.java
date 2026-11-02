package dev.recruitflow.audit;

import dev.recruitflow.audit.domain.AuditLog;
import dev.recruitflow.audit.repository.AuditLogRepository;
import dev.recruitflow.shared.web.ClientInfo;
import dev.recruitflow.shared.web.CorrelationIdFilter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade of the audit module. Records commit in their own transaction so a failed or rolled-back
 * business operation (for example a rejected login) still leaves its audit trail.
 * Details must contain ids and field names only, never PII values.
 */
@Component
public class AuditRecorder {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditRecorder(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID actorId, String entityType, UUID entityId, Map<String, Object> details) {
        repository.save(new AuditLog(actorId, action, entityType, entityId, details,
                ClientInfo.current().ip(), MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant()));
    }

    public void record(String action, UUID actorId, String entityType, UUID entityId) {
        record(action, actorId, entityType, entityId, Map.of());
    }
}
