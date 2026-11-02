package dev.recruitflow.identity.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.identity.domain.User;
import dev.recruitflow.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from configuration when the users table is empty.
 * The account must change its password on first login, so the configured one is short-lived.
 */
@Component
@ConditionalOnProperty("recruitflow.bootstrap.admin-email")
class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditRecorder audit;
    private final String email;
    private final String password;

    BootstrapAdminRunner(UserRepository users, PasswordEncoder encoder, PasswordPolicy passwordPolicy, AuditRecorder audit,
                         @Value("${recruitflow.bootstrap.admin-email}") String email,
                         @Value("${recruitflow.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        passwordPolicy.validate("recruitflow.bootstrap.admin-password", password);
        User admin = users.save(User.bootstrapAdmin(Emails.normalize(email), encoder.encode(password)));
        audit.record("BOOTSTRAP_ADMIN_CREATED", null, "USER", admin.getId());
        log.info("Bootstrap admin account created (id {}); it must change its password on first login.", admin.getId());
    }
}
