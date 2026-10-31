package com.smartdroneinspection.users.service;

import com.smartdroneinspection.users.domain.enums.LoginOtpChannel;

public interface LoginOtpDelivery {

  void deliver(LoginOtpChannel channel, String destination, String code);
}
