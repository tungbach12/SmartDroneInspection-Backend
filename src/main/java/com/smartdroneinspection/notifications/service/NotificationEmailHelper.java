package com.smartdroneinspection.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Best-effort bilingual email dispatcher for critical notifications. */
@Service
public class NotificationEmailHelper {

  private static final Logger log = LoggerFactory.getLogger(NotificationEmailHelper.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String mailFrom;

  public NotificationEmailHelper(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${app.auth.otp.email-from:${spring.mail.username:}}") String mailFrom) {
    this.mailSender = mailSender;
    this.mailFrom = mailFrom;
  }

  public void sendBilingualBestEffort(
      String toEmail, String recipientName, String subject, String viBody, String enBody) {
    if (mailSender == null || mailFrom == null || mailFrom.isBlank()) {
      return;
    }
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null || toEmail == null || toEmail.isBlank()) {
      return;
    }
    try {
      String displayName =
          recipientName != null && !recipientName.isBlank() ? recipientName.trim() : "User";
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailFrom);
      message.setTo(toEmail.trim());
      message.setSubject("[SmartDroneInspection] " + subject);
      message.setText(
          "Xin chào "
              + displayName
              + ",\n\n"
              + viBody
              + "\n\n---\n\nHello "
              + displayName
              + ",\n\n"
              + enBody
              + "\n\nTrân trọng / Best regards,\nSmartDroneInspection");
      sender.send(message);
    } catch (Exception ex) {
      log.warn("Failed to send notification email to {}: {}", toEmail, ex.getMessage());
    }
  }
}
