package dev.recruitflow.pipeline.domain;

/**
 * Pure rules for moving an application, kept free of Spring and persistence so they can be tested exhaustively.
 */
public final class TransitionPolicy {

    private TransitionPolicy() {}

    public static boolean isLegal(ApplicationStatus from, ApplicationStatus to) {
        return from.canMoveTo(to);
    }

    /**
     * Moves that commit the firm to something (making an offer, hiring) or undo a decision (reopening a rejection)
     * are reserved for the recruiter who owns the application or an admin.
     */
    public static boolean needsOwnerOrAdmin(ApplicationStatus from, ApplicationStatus to) {
        return to == ApplicationStatus.OFFER || to == ApplicationStatus.HIRED
                || (from == ApplicationStatus.REJECTED && to == ApplicationStatus.NEW);
    }

    public static boolean requiresReason(ApplicationStatus to) {
        return to == ApplicationStatus.REJECTED;
    }
}
