package dev.recruitflow.interview.service;

import dev.recruitflow.interview.domain.InterviewStatus;
import dev.recruitflow.interview.domain.InterviewType;
import dev.recruitflow.interview.domain.ParticipantRole;
import dev.recruitflow.interview.domain.Recommendation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models for interviews. They never include a candidate's contact details, so interviewers cannot see them. */
public final class InterviewViews {

    private InterviewViews() {}

    public record ParticipantView(UUID userId, String name, ParticipantRole role, boolean feedbackSubmitted) {
    }

    /**
     * candidateId is only set for recruiters and admins (interviewers have no candidate pages).
     * canManage / canGiveFeedback tell the UI what to offer; the server enforces the same rules.
     */
    public record InterviewView(UUID id, UUID applicationId, UUID candidateId, String candidateName, String candidateTitle,
                                UUID jobId, String jobTitle, String clientName, String applicationStatus, int roundNo,
                                InterviewType type, Instant scheduledStart, Instant scheduledEnd, String locationOrLink,
                                InterviewStatus status, String notes, List<ParticipantView> participants,
                                boolean canManage, boolean canGiveFeedback, Long version) {
    }

    public record FeedbackView(UUID id, UUID interviewerId, String interviewerName, int rating,
                               Recommendation recommendation, String strengths, String concerns, Instant submittedAt,
                               boolean mine) {
    }

    /** hiddenCount tells an interviewer how many colleagues have already given feedback they cannot see yet. */
    public record FeedbackList(List<FeedbackView> feedback, boolean blinded, int hiddenCount) {
    }
}
