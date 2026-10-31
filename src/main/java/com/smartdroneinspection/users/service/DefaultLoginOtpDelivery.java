package com.smartdroneinspection.users.service;

import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultLoginOtpDelivery implements LoginOtpDelivery {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final LoginOtpProperties properties;
  private final RestClient restClient;

  public DefaultLoginOtpDelivery(
      ObjectProvider<JavaMailSender> mailSender, LoginOtpProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
    this.restClient = RestClient.builder().build();
  }

  @Override
  public void deliver(LoginOtpChannel channel, String destination, String code) {
    switch (channel) {
      case EMAIL -> sendEmail(destination, code);
      case SMS -> sendSms(destination, code);
    }
  }

  private void sendEmail(String destination, String code) {
    if (properties.getEmailFrom().isBlank() || properties.getSmtpHost().isBlank()) {
      throw new IllegalStateException("OTP email sender is not configured");
    }
    JavaMailSender sender =
        mailSender.getIfAvailable(
            () -> {
              throw new IllegalStateException("OTP email transport is not configured");
            });
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.getEmailFrom());
    message.setTo(destination);
    message.setSubject("Your SmartDroneInspection sign-in code");
    message.setText("Your one-time sign-in code is " + code + ". It expires in five minutes.");
    sender.send(message);
  }

  private void sendSms(String destination, String code) {
    if (properties.getSmsAccountSid().isBlank()
        || properties.getSmsAuthToken().isBlank()
        || properties.getSmsFromNumber().isBlank()) {
      throw new IllegalStateException("OTP SMS sender is not configured");
    }
    String form =
        "To="
            + encode(destination)
            + "&From="
            + encode(properties.getSmsFromNumber())
            + "&Body="
            + encode(
                "Your SmartDroneInspection sign-in code is "
                    + code
                    + ". It expires in five minutes.");
    String basicAuth =
        Base64.getEncoder()
            .encodeToString(
                (properties.getSmsAccountSid() + ":" + properties.getSmsAuthToken())
                    .getBytes(StandardCharsets.UTF_8));
    restClient
        .post()
        .uri(
            "https://api.twilio.com/2010-04-01/Accounts/"
                + properties.getSmsAccountSid()
                + "/Messages.json")
        .header("Authorization", "Basic " + basicAuth)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
