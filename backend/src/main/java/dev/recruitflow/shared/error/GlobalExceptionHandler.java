package dev.recruitflow.shared.error;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Single place that turns exceptions into RFC 7807 responses. Never leaks stack traces or PII. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException ex) {
        ProblemDetail problem = Problems.of(ex.code(), ex.getMessage());
        ex.extensions().forEach(problem::setProperty);
        return ResponseEntity.status(ex.code().status()).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleStale(OptimisticLockingFailureException ex) {
        return handleDomain(new DomainException(ErrorCode.STALE_VERSION,
                "The resource was modified by someone else. Reload and retry."));
    }

    /** Method-security denials are thrown inside controllers, so they reach advice before the security filters. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return handleDomain(new DomainException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
        return handleDomain(new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Problems.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Framework-level errors (malformed JSON, unsupported media type, ...) keep Spring's status but get our shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail original = body instanceof ProblemDetail pd ? pd : null;
        String code;
        String detail;
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            code = ErrorCode.RESOURCE_NOT_FOUND.name();
            detail = "The requested resource was not found.";
        } else if (statusCode.is4xxClientError()) {
            code = "BAD_REQUEST";
            detail = "The request could not be processed.";
        } else {
            code = ErrorCode.INTERNAL_ERROR.name();
            detail = "An unexpected error occurred.";
        }
        String title = original != null && original.getTitle() != null ? original.getTitle() : code;
        ProblemDetail problem = Problems.of(statusCode, code, title, detail);
        if (original != null && original.getInstance() != null) {
            problem.setInstance(original.getInstance());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }
}
