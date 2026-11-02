package dev.recruitflow.shared.error;

import java.util.Map;

/** Base type for expected business failures. Mapped to RFC 7807 by {@link GlobalExceptionHandler}. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> extensions;

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public DomainException(ErrorCode code, String detail, Map<String, Object> extensions) {
        super(detail);
        this.code = code;
        this.extensions = Map.copyOf(extensions);
    }

    public ErrorCode code() {
        return code;
    }

    /** Extra problem+json members (for example existingCandidateId). Must not contain PII. */
    public Map<String, Object> extensions() {
        return extensions;
    }
}
