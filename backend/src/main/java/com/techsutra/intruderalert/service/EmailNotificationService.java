package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.config.NotificationProperties;
import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.model.AlertRecord;
import com.techsutra.intruderalert.repository.UserAccountRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EmailNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender javaMailSender;
    private final UserAccountRepository userAccountRepository;
    private final NotificationProperties notificationProperties;
    private final SystemSettingsService systemSettingsService;

    public EmailNotificationService(
            JavaMailSender javaMailSender,
            UserAccountRepository userAccountRepository,
            NotificationProperties notificationProperties,
            SystemSettingsService systemSettingsService
    ) {
        this.javaMailSender = javaMailSender;
        this.userAccountRepository = userAccountRepository;
        this.notificationProperties = notificationProperties;
        this.systemSettingsService = systemSettingsService;
    }

    @Async
    public void sendIntruderAlertEmail(AlertRecord alertRecord, byte[] snapshotBytes) {
        if (!systemSettingsService.isEmailEnabled()) {
            return;
        }

        if (notificationProperties.getFromEmail() == null || notificationProperties.getFromEmail().isBlank()) {
            LOGGER.warn("Email notifications are enabled, but app.notifications.from-email is blank.");
            return;
        }

        List<String> recipients = userAccountRepository.findAll().stream()
                .map(UserAccount::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            LOGGER.info("Skipping alert email because no registered recipients were found.");
            return;
        }

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(notificationProperties.getFromEmail());
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject("Intruder Alert: Unauthorized person detected");
            helper.setText(buildHtmlBody(alertRecord), true);
            helper.addAttachment(
                    alertRecord.getFileName(),
                    new ByteArrayResource(snapshotBytes),
                    "image/jpeg"
            );
            javaMailSender.send(mimeMessage);
            LOGGER.info("Intruder alert email sent to {} recipient(s).", recipients.size());
        } catch (MailAuthenticationException exception) {
            LOGGER.warn("Gmail rejected the configured credentials. Check the app password and sender email.");
        } catch (MessagingException | MailException exception) {
            LOGGER.warn("Failed to send intruder alert email: {}", exception.getMessage());
        }
    }

    private String buildHtmlBody(AlertRecord alertRecord) {
        return """
                <div style="font-family:Arial,sans-serif;line-height:1.6;color:#111827;">
                  <h2 style="margin-bottom:8px;">Intruder Alert</h2>
                  <p>An unauthorized person was detected by your security system.</p>
                  <table style="border-collapse:collapse;margin:16px 0;">
                    <tr><td style="padding:6px 12px 6px 0;"><strong>Camera</strong></td><td>%s</td></tr>
                    <tr><td style="padding:6px 12px 6px 0;"><strong>Time</strong></td><td>%s</td></tr>
                    <tr><td style="padding:6px 12px 6px 0;"><strong>Severity</strong></td><td>%s</td></tr>
                    <tr><td style="padding:6px 12px 6px 0;"><strong>Message</strong></td><td>%s</td></tr>
                  </table>
                  <p>The latest snapshot is attached to this email.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 16px;border-radius:999px;background:#ff7a45;color:#ffffff;text-decoration:none;">Open Dashboard</a></p>
                </div>
                """.formatted(
                escapeHtml(alertRecord.getCameraId()),
                escapeHtml(alertRecord.getTimestamp()),
                escapeHtml(alertRecord.getSeverity()),
                escapeHtml(alertRecord.getMessage()),
                systemSettingsService.getDashboardBaseUrl()
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
