package dev.recruitflow.pipeline.domain;

/** Where an application came from. Mirrors the candidate sources but is owned by this module. */
public enum ApplicationSource {
    LINKEDIN,
    REFERRAL,
    JOB_BOARD,
    AGENCY,
    OTHER
}
