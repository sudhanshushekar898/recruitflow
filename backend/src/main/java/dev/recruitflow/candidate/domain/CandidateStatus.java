package dev.recruitflow.candidate.domain;

public enum CandidateStatus {
    ACTIVE,
    ARCHIVED,
    DO_NOT_CONTACT,
    /** Personal data erased on request (M11). Never listed or searched. */
    ANONYMISED
}
