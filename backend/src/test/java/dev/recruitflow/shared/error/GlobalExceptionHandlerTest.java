package dev.recruitflow.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.recruitflow.shared.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void mapsDomainExceptionToProblemWithCodeAndExtensions() throws Exception {
        mvc.perform(get("/stub/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE_CANDIDATE"))
                .andExpect(jsonPath("$.existingCandidateId").value("abc"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsFieldErrorsForInvalidBody() throws Exception {
        mvc.perform(post("/stub/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void hidesInternalDetailsOnUnexpectedError() throws Exception {
        mvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void mapsMalformedJsonToBadRequestWithoutParserDetails() throws Exception {
        mvc.perform(post("/stub/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("The request could not be processed."));
    }

    @Test
    void mapsUnknownRouteToResourceNotFound() throws Exception {
        mvc.perform(get("/stub/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void echoesValidInboundCorrelationIdAndReplacesUnsafeOne() throws Exception {
        mvc.perform(get("/stub/conflict").header("X-Request-Id", "req-123"))
                .andExpect(header().string("X-Request-Id", "req-123"))
                .andExpect(jsonPath("$.correlationId").value("req-123"));

        String unsafe = "bad id\nwith newline";
        mvc.perform(get("/stub/conflict").header("X-Request-Id", unsafe))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not(unsafe)));
    }

    @RestController
    static class StubController {
        record Body(@NotBlank String name) {}

        @GetMapping("/stub/conflict")
        void conflict() {
            throw new DomainException(ErrorCode.DUPLICATE_CANDIDATE, "exists", Map.of("existingCandidateId", "abc"));
        }

        @GetMapping("/stub/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/stub/validate")
        void validate(@Valid @RequestBody Body body) {}
    }
}
