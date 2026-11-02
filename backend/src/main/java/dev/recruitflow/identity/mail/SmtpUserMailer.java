package dev.recruitflow.identity.mail;

import dev.recruitflow.identity.config.AppProperties;
import dev.recruitflow.identity.service.UserMailer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpUserMailer implements UserMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpUserMailer.class);

    private final JavaMailSender sender;
    private final AppProperties app;

    SmtpUserMailer(JavaMailSender sender, AppProperties app) {
        this.sender = sender;
        this.app = app;
    }

    @Override
    public void sendInvite(UUID userId, String toAddress, String firstName, String link) {
        send(userId, toAddress, "You have been invited to RecruitFlow",
                "Hi " + firstName + ",\n\nAn account has been created for you. Set your password here (valid for 24 hours):\n"
                        + link + "\n\nIf you did not expect this email, ignore it.");
    }

    @Override
    public void sendPasswordReset(UUID userId, String toAddress, String firstName, String link) {
        send(userId, toAddress, "Reset your RecruitFlow password",
                "Hi " + firstName + ",\n\nUse this link to choose a new password (valid for 1 hour):\n"
                        + link + "\n\nIf you did not request this, ignore this email.");
    }

    private void send(UUID userId, String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(app.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            // Never log the address or link; the user id is enough to follow up.
            log.warn("Could not send account email for user {}: {}", userId, e.getClass().getSimpleName());
        }
    }
}
