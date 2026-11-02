package dev.recruitflow.shared.error;

import dev.recruitflow.shared.web.CorrelationIdFilter;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 bodies with the stable members every error response carries (code, correlationId). */
public final class Problems {

    private static final String PROBLEM_BASE = "https://recruitflow.dev/problems/";

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        decorate(problem, code.name());
        return problem;
    }

    /** For framework errors that have no ErrorCode of their own (for example malformed JSON). */
    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        decorate(problem, code);
        return problem;
    }

    private static void decorate(ProblemDetail problem, String code) {
        problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
