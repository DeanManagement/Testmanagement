package com.deanmanagement.testmanagement.project.internal.notification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends optional notification emails. Air-gap safe: a no-op unless {@code app.mail.enabled=true} and
 * a {@link JavaMailSender} is configured. Runs asynchronously so it never blocks the request thread.
 */
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@testmanagement.local}")
    private String fromAddress;

    public boolean isEnabled() {
        return mailEnabled;
    }

    @Async
    public void send(String toEmail, String subject, String body) {
        if (!mailEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("app.mail.enabled=true but no JavaMailSender is configured; skipping email");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send notification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
