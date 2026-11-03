package dev.recruitflow.job.api;

import dev.recruitflow.job.domain.EmploymentType;
import dev.recruitflow.job.domain.JobStatus;
import dev.recruitflow.job.domain.SkillImportance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request bodies of the /clients and /jobs endpoints. */
final class JobDtos {

    private JobDtos() {}

    static final String CURRENCY = "^[A-Z]{3}$";

    record CreateClientRequest(@NotBlank @Size(max = 150) String name, @Size(max = 100) String industry,
                               @Size(max = 150) String contactName, @Email @Size(max = 254) String contactEmail) {
    }

    /** Absent fields stay unchanged; empty strings clear optional text. */
    record UpdateClientRequest(@Size(min = 1, max = 150) String name, @Size(max = 100) String industry,
                               @Size(max = 150) String contactName, @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+$") @Size(max = 254) String contactEmail,
                               Boolean active, @NotNull Long version) {
    }

    record SkillRequirementRequest(@NotNull UUID skillId, @NotNull SkillImportance importance,
                                   @Min(0) @Max(60) Integer minYears, @Min(1) @Max(5) Integer weight) {
    }

    record CreateJobRequest(@NotNull UUID clientId, @NotBlank @Size(max = 150) String title,
                            @Size(max = 10000) String description, @Size(max = 150) String location,
                            @NotNull EmploymentType employmentType,
                            @DecimalMin("0") BigDecimal salaryMin, @DecimalMin("0") BigDecimal salaryMax,
                            @Pattern(regexp = CURRENCY) String currency,
                            @Min(1) @Max(1000) Integer openings,
                            @Valid @Size(max = 50) List<SkillRequirementRequest> skills) {
    }

    record UpdateJobRequest(UUID clientId, @Size(min = 1, max = 150) String title, @Size(max = 10000) String description,
                            @Size(max = 150) String location, EmploymentType employmentType,
                            @DecimalMin("0") BigDecimal salaryMin, @DecimalMin("0") BigDecimal salaryMax,
                            @Pattern(regexp = CURRENCY) String currency, @Min(1) @Max(1000) Integer openings,
                            @NotNull Long version) {
    }

    record ReplaceJobSkillsRequest(@NotNull @Valid @Size(max = 50) List<SkillRequirementRequest> skills) {
    }

    record ChangeJobStatusRequest(@NotNull JobStatus toStatus) {
    }
}
