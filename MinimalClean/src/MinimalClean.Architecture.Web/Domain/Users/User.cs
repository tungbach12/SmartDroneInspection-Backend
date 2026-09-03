using MinimalClean.Architecture.Web.Domain.Common;
using MinimalClean.Architecture.Web.Domain.Users.Enums;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Users;

public class User : EntityBase<User, UserId>, IAuditable, ISoftDelete, IAggregateRoot
{
    private User() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public User(
        string email = default!,
        string normalizedEmail = default!,
        string username = default!,
        string normalizedUsername = default!,
        string passwordHash = default!,
        string fullName = default!,
        UserRole role = default!,
        bool mustChangePassword = default,
        int failedLoginCount = default,
        Guid? organizationId = default!,
        string? phone = default!,
        bool isActive = true,
        DateTime? lastLoginAt = default!,
        string? lastLoginIp = default!,
        DateTime? passwordChangedAt = default!,
        DateTime? lockoutEndAt = default!,
        string? avatarUrl = default!)  
    {
        Email = Guard.Against.NullOrWhiteSpace(email, nameof(email));
        NormalizedEmail = Guard.Against.NullOrWhiteSpace(normalizedEmail, nameof(normalizedEmail));
        Username = Guard.Against.NullOrWhiteSpace(username, nameof(username));
        NormalizedUsername = Guard.Against.NullOrWhiteSpace(normalizedUsername, nameof(normalizedUsername));
        PasswordHash = Guard.Against.NullOrWhiteSpace(passwordHash, nameof(passwordHash));
        FullName = Guard.Against.NullOrWhiteSpace(fullName, nameof(fullName));
        Role = role;
        MustChangePassword = mustChangePassword;
        FailedLoginCount = failedLoginCount;
        OrganizationId = organizationId;
        Phone = phone;
        IsActive = isActive;
        LastLoginAt = lastLoginAt;
        LastLoginIp = lastLoginIp;
        PasswordChangedAt = passwordChangedAt;
        LockoutEndAt = lockoutEndAt;
        AvatarUrl = avatarUrl;
    }

    public Guid? OrganizationId { get; private set; }
    public string Email { get; private set; } = string.Empty;
    public string NormalizedEmail { get; private set; } = string.Empty;
    public string Username { get; private set; } = string.Empty;
    public string NormalizedUsername { get; private set; } = string.Empty;
    public string PasswordHash { get; private set; } = string.Empty;
    public string FullName { get; private set; } = string.Empty;
    public string? Phone { get; private set; }
    public UserRole Role { get; private set; } = default!;
    public bool IsActive { get; private set; } = true;
    public DateTime? LastLoginAt { get; private set; }
    public string? LastLoginIp { get; private set; }
    public DateTime? PasswordChangedAt { get; private set; }
    public bool MustChangePassword { get; private set; }
    public int FailedLoginCount { get; private set; }
    public DateTime? LockoutEndAt { get; private set; }
    public string? AvatarUrl { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public User UpdateOrganizationId(Guid? newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public User UpdateEmail(string newEmail)
    {
        Email = Guard.Against.NullOrWhiteSpace(newEmail, nameof(newEmail));
        return this;
    }

    public User UpdateNormalizedEmail(string newNormalizedEmail)
    {
        NormalizedEmail = Guard.Against.NullOrWhiteSpace(newNormalizedEmail, nameof(newNormalizedEmail));
        return this;
    }

    public User UpdateUsername(string newUsername)
    {
        Username = Guard.Against.NullOrWhiteSpace(newUsername, nameof(newUsername));
        return this;
    }

    public User UpdateNormalizedUsername(string newNormalizedUsername)
    {
        NormalizedUsername = Guard.Against.NullOrWhiteSpace(newNormalizedUsername, nameof(newNormalizedUsername));
        return this;
    }

    public User UpdatePasswordHash(string newPasswordHash)
    {
        PasswordHash = Guard.Against.NullOrWhiteSpace(newPasswordHash, nameof(newPasswordHash));
        return this;
    }

    public User UpdateFullName(string newFullName)
    {
        FullName = Guard.Against.NullOrWhiteSpace(newFullName, nameof(newFullName));
        return this;
    }

    public User UpdatePhone(string? newPhone)
    {
        Phone = newPhone;
        return this;
    }

    public User UpdateRole(UserRole newRole)
    {
        Role = newRole;
        return this;
    }

    public User UpdateIsActive(bool newIsActive)
    {
        IsActive = newIsActive;
        return this;
    }

    public User UpdateLastLoginAt(DateTime? newLastLoginAt)
    {
        LastLoginAt = newLastLoginAt;
        return this;
    }

    public User UpdateLastLoginIp(string? newLastLoginIp)
    {
        LastLoginIp = newLastLoginIp;
        return this;
    }

    public User UpdatePasswordChangedAt(DateTime? newPasswordChangedAt)
    {
        PasswordChangedAt = newPasswordChangedAt;
        return this;
    }

    public User UpdateMustChangePassword(bool newMustChangePassword)
    {
        MustChangePassword = newMustChangePassword;
        return this;
    }

    public User UpdateFailedLoginCount(int newFailedLoginCount)
    {
        FailedLoginCount = newFailedLoginCount;
        return this;
    }

    public User UpdateLockoutEndAt(DateTime? newLockoutEndAt)
    {
        LockoutEndAt = newLockoutEndAt;
        return this;
    }

    public User UpdateAvatarUrl(string? newAvatarUrl)
    {
        AvatarUrl = newAvatarUrl;
        return this;
    }

}
