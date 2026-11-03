package dev.recruitflow.shared.error;

import org.springframework.http.HttpStatus;

/** Stable, client-facing error codes. Catalogue: docs/ARCHITECTURE.md section 7.2. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account temporarily locked"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "Invalid or expired token"),
    DUPLICATE_USER(HttpStatus.CONFLICT, "User already exists"),
    DUPLICATE_SKILL(HttpStatus.CONFLICT, "Skill already exists"),
    DUPLICATE_CLIENT(HttpStatus.CONFLICT, "Client already exists"),
    INVALID_OPERATION(HttpStatus.UNPROCESSABLE_ENTITY, "Operation not allowed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DUPLICATE_CANDIDATE(HttpStatus.CONFLICT, "Candidate already exists"),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "Application already exists"),
    STALE_VERSION(HttpStatus.CONFLICT, "Resource was modified by someone else"),
    INTERVIEW_CONFLICT(HttpStatus.CONFLICT, "Interview time conflict"),
    FEEDBACK_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Feedback already submitted"),
    INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid status transition"),
    JOB_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "Job is not open"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type not allowed"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
