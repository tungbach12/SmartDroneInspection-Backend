package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.config.AuthProperties;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.auth.bootstrap.enabled", havingValue = "true")
public class BootstrapAdministrator implements ApplicationRunner {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final PasswordPolicy passwordPolicy;
  private final AuthProperties properties;

  public BootstrapAdministrator(
      UserRepository users,
      PasswordEncoder passwords,
      PasswordPolicy passwordPolicy,
      AuthProperties properties) {
    this.users = users;
    this.passwords = passwords;
    this.passwordPolicy = passwordPolicy;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.existsByRoleAssignments_Role(UserRole.ADMIN)) {
      return;
    }
    var bootstrap = properties.getBootstrap();
    String email = bootstrap.getEmail();
    String password = bootstrap.getPassword();
    String fullName = bootstrap.getFullName();
    if (email.isBlank() || password.isBlank()) {
      throw new IllegalStateException(
          "Bootstrap is enabled but AUTH_BOOTSTRAP_EMAIL or AUTH_BOOTSTRAP_PASSWORD is missing.");
    }
    passwordPolicy.validate(password, email, fullName);
    User administrator =
        new User(
            email.trim(),
            fullName.trim(),
            passwords.encode(password),
            UserStatus.ACTIVE,
            ActorZone.PLATFORM,
            null);
    administrator.addRole(UserRole.ADMIN);
    administrator.requirePasswordChange();
    users.save(administrator);
  }
}
