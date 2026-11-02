package dev.recruitflow.candidate.api;

import dev.recruitflow.candidate.domain.CandidateSource;
import dev.recruitflow.candidate.domain.LegalBasis;
import dev.recruitflow.candidate.domain.SkillLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies of the /candidates endpoints. */
final class CandidateDtos {

    private CandidateDtos() {}

    /** Digits, spaces and + ( ) -; an empty string is allowed so PATCH can clear the field. */
    static final String PHONE = "^$|^[+0-9 ()\\-]{5,30}$";
    static final String HTTPS_URL = "^$|^https://\\S{3,290}$";

    record SkillEntryRequest(@NotNull UUID skillId, @Min(0) @Max(60) Integer years, SkillLevel level) {
    }

    record ConsentRequest(@NotNull LegalBasis legalBasis, @NotNull Instant givenAt, Instant expiresAt,
                          @Size(max = 300) String evidence, boolean withdrawn) {
    }

    record CreateCandidateRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE) @Size(max = 30) String phone,
            @Size(max = 150) String currentTitle,
            @Size(max = 150) String currentCompany,
            @Size(max = 150) String location,
            @Min(0) @Max(60) Integer yearsExperience,
            @NotNull CandidateSource source,
            @Size(max = 5000) String summary,
            @Pattern(regexp = HTTPS_URL) @Size(max = 300) String linkedinUrl,
            @Valid @Size(max = 100) List<SkillEntryRequest> skills,
            @Valid ConsentRequest consent) {
    }

    /** Absent (null) fields stay unchanged; empty strings clear optional text fields. */
    record UpdateCandidateRequest(
            @Size(min = 1, max = 100) String firstName,
            @Size(min = 1, max = 100) String lastName,
            @Email @Size(min = 1, max = 254) String email,
            @Pattern(regexp = PHONE) @Size(max = 30) String phone,
            @Size(max = 150) String currentTitle,
            @Size(max = 150) String currentCompany,
            @Size(max = 150) String location,
            @Min(0) @Max(60) Integer yearsExperience,
            CandidateSource source,
            @Size(max = 5000) String summary,
            @Pattern(regexp = HTTPS_URL) @Size(max = 300) String linkedinUrl,
            @NotNull Long version) {
    }

    record ReplaceSkillsRequest(@NotNull @Valid @Size(max = 100) List<SkillEntryRequest> skills) {
    }

    record CreateNoteRequest(@NotBlank @Size(max = 4000) String body) {
    }
}
