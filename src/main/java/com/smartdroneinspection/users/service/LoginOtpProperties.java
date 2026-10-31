package com.smartdroneinspection.users.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.otp")
public class LoginOtpProperties {

  private boolean enabled = true;
  private String challengePepper = "";
  private String smsAccountSid = "";
  private String smsAuthToken = "";
  private String smsFromNumber = "";
  private String emailFrom = "";
  private String smtpHost = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getChallengePepper() {
    return challengePepper;
  }

  public void setChallengePepper(String challengePepper) {
    this.challengePepper = challengePepper;
  }

  public String getSmsAccountSid() {
    return smsAccountSid;
  }

  public void setSmsAccountSid(String smsAccountSid) {
    this.smsAccountSid = smsAccountSid;
  }

  public String getSmsAuthToken() {
    return smsAuthToken;
  }

  public void setSmsAuthToken(String smsAuthToken) {
    this.smsAuthToken = smsAuthToken;
  }

  public String getSmsFromNumber() {
    return smsFromNumber;
  }

  public void setSmsFromNumber(String smsFromNumber) {
    this.smsFromNumber = smsFromNumber;
  }

  public String getEmailFrom() {
    return emailFrom;
  }

  public void setEmailFrom(String emailFrom) {
    this.emailFrom = emailFrom;
  }

  public String getSmtpHost() {
    return smtpHost;
  }

  public void setSmtpHost(String smtpHost) {
    this.smtpHost = smtpHost;
  }
}
