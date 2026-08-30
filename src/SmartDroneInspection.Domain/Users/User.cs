using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Users;

public class User : BaseEntity, IAuditable, ISoftDelete
{
    public Guid? OrganizationId { get; set; }
    public string Email { get; set; } = string.Empty;
    public string NormalizedEmail { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string NormalizedUsername { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public UserRole Role { get; set; }
    public bool IsActive { get; set; } = true;
    public DateTime? LastLoginAt { get; set; }
    public string? LastLoginIp { get; set; }
    public DateTime? PasswordChangedAt { get; set; }
    public bool MustChangePassword { get; set; }
    public int FailedLoginCount { get; set; }
    public DateTime? LockoutEndAt { get; set; }
    public string? AvatarUrl { get; set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
}
