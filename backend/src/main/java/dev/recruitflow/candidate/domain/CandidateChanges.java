package dev.recruitflow.candidate.domain;

/**
 * Partial update of a candidate profile. A null field means "leave unchanged"; an empty string clears an
 * optional text field.
 */
public record CandidateChanges(
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        String currentCompany,
        String location,
        Integer yearsExperience,
        CandidateSource source,
        String summary,
        String linkedinUrl) {
}
