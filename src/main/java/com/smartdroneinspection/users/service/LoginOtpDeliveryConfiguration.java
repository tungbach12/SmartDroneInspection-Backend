package com.smartdroneinspection.users.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginOtpDeliveryConfiguration {

  @Bean
  @ConditionalOnMissingBean(LoginOtpDelivery.class)
  @ConditionalOnProperty(
      prefix = "smartdroneinspection.auth.otp",
      name = "enabled",
      havingValue = "false")
  LoginOtpDelivery disabledLoginOtpDelivery() {
    return (channel, destination, code) -> {
      throw new IllegalStateException("Inspector web OTP is disabled");
    };
  }
}
