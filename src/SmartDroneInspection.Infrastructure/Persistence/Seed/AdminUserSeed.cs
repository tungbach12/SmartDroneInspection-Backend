using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Persistence.Seed;

/// <summary>
/// Idempotent dev seed: ensures a default organization and admin user exist so
/// the API can be hit immediately after `docker compose up` + migration.
/// In production, the seeder is a no-op unless <c>Seed:Enabled</c> is true.
/// </summary>
public sealed class AdminUserSeed(
    IApplicationDbContext db,
    IPasswordHasher hasher,
    IConfiguration configuration)
{
    public const string DefaultOrgCode = "DEFAULT";
    public const string DefaultAdminEmail = "admin@sdi.local";
    public const string DefaultAdminPassword = "ChangeMe!2026";

    public async Task EnsureSeedAsync()
    {
        if (!configuration.GetValue("Seed:Enabled", true))
        {
            return;
        }

        var normalizedEmail = DefaultAdminEmail.ToUpperInvariant();
        var existingAdmin = await db.Users
            .FirstOrDefaultAsync(u => u.NormalizedEmail == normalizedEmail);
        if (existingAdmin is not null)
        {
            return;
        }

        var org = await db.Organizations.FirstOrDefaultAsync(o => o.Code == DefaultOrgCode);
        if (org is null)
        {
            org = new Organization
            {
                Code = DefaultOrgCode,
                Name = "Default Organization",
                IsActive = true,
            };
            db.Organizations.Add(org);
            await db.SaveChangesAsync();
        }

        var admin = new User
        {
            OrganizationId = org.Id,
            Email = DefaultAdminEmail,
            NormalizedEmail = normalizedEmail,
            Username = "admin",
            NormalizedUsername = "ADMIN",
            FullName = "System Administrator",
            PasswordHash = hasher.HashPassword(DefaultAdminPassword),
            Role = UserRole.Administrator,
            IsActive = true,
            MustChangePassword = true,
        };
        db.Users.Add(admin);
        await db.SaveChangesAsync();
    }
}
