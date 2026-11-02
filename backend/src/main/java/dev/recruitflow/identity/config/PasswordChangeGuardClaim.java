package dev.recruitflow.identity.config;

/** Name of the access-token claim that marks a user who must change their password before doing anything else. */
public final class PasswordChangeGuardClaim {

    public static final String NAME = "mcp";

    private PasswordChangeGuardClaim() {}
}
