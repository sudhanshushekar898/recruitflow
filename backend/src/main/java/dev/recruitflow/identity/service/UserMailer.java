package dev.recruitflow.identity.service;

import java.util.UUID;

/**
 * Port for account emails. Sent synchronously after commit for now; M10 replaces the adapter with the
 * transactional outbox. Implementations must not log addresses or links.
 */
public interface UserMailer {

    void sendInvite(UUID userId, String toAddress, String firstName, String link);

    void sendPasswordReset(UUID userId, String toAddress, String firstName, String link);
}
