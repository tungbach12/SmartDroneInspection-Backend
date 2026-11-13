package com.smartdroneinspection.assets;

import com.smartdroneinspection.assets.domain.AssetCategory;
import com.smartdroneinspection.assets.repository.AssetCategoryRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.ActorZone;
import com.smartdroneinspection.users.domain.enums.UserRole;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.repository.UserRepository;
import java.util.UUID;

/** Users and category shared by the asset tests. */
public final class AssetTestFixture {

  private final AssetCategoryRepository categories;
  private final AssetRepository assets;
  private final UserRepository users;

  public AssetTestFixture(
      AssetCategoryRepository categories, AssetRepository assets, UserRepository users) {
    this.categories = categories;
    this.assets = assets;
    this.users = users;
  }

  public Data create() {
    User client = saveUser("client", UserStatus.ACTIVE, ActorZone.CUSTOMER, UserRole.CUSTOMER);
    User otherClient =
        saveUser("other-client", UserStatus.ACTIVE, ActorZone.CUSTOMER, UserRole.CUSTOMER);
    User manager = saveUser("manager", UserStatus.ACTIVE, ActorZone.CUSTOMER, UserRole.CUSTOMER);
    User admin = saveUser("admin", UserStatus.ACTIVE, ActorZone.PLATFORM, UserRole.ADMIN);

    AssetCategory category =
        categories.saveAndFlush(
            new AssetCategory("bridge-" + client.getId(), "Bridge", "Bridge infrastructure", true));

    return new Data(
        client.getId(), otherClient.getId(), manager.getId(), admin.getId(), category.getId());
  }

  private User saveUser(String label, UserStatus status, ActorZone actorZone, UserRole role) {
    UUID id = UUID.randomUUID();
    String email = label + "-" + id + "@example.test";
    User user = new User(email, label, "{noop}password", status, actorZone);
    user.addRole(role);
    return users.saveAndFlush(user);
  }

  public record Data(
      UUID clientId, UUID otherClientId, UUID managerId, UUID adminId, UUID categoryId) {}
}
