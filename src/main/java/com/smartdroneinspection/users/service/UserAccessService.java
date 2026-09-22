package com.smartdroneinspection.users.service;

import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccessService implements UserAccess {

  private final UserRepository users;

  public UserAccessService(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActiveUser> findActiveUser(UUID userId) {
    return users
        .findDetailedById(userId)
        .filter(user -> user.active())
        .map(user -> new ActiveUser(user.getId(), Set.copyOf(user.roleValues())));
  }
}
