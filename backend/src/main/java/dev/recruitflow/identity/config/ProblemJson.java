package dev.recruitflow.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/** Writes problem+json from servlet filters, which sit outside Spring MVC's exception handling. */
final class ProblemJson {

    private ProblemJson() {}

    static void write(HttpServletResponse response, ObjectMapper mapper, ProblemDetail problem, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
