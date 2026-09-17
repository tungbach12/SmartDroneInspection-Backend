package com.smartdroneinspection.users.service;

import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginFailureService {

  private final UserRepository users;

  public LoginFailureService(UserRepository users) {
    this.users = users;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID userId) {
    users.findById(userId).ifPresent(user -> user.recordFailedLogin());
  }
}
