using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;

namespace Clean.Architecture.Infrastructure.Data.Config.Users;

public sealed class UserConfiguration : IEntityTypeConfiguration<User>
{
    public void Configure(EntityTypeBuilder<User> builder)
    {
        builder.ConfigureBase("users");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit(); builder.ConfigureSoftDelete();
        builder.Property(x => x.Email).HasMaxLength(320).IsRequired();
        builder.Property(x => x.NormalizedEmail).HasMaxLength(320).IsRequired();
        builder.Property(x => x.Username).HasMaxLength(100).IsRequired();
        builder.Property(x => x.NormalizedUsername).HasMaxLength(100).IsRequired();
        builder.Property(x => x.PasswordHash).HasMaxLength(512).IsRequired();
        builder.Property(x => x.FullName).HasMaxLength(200).IsRequired();
        builder.Property(x => x.Phone).HasMaxLength(32); builder.Property(x => x.LastLoginIp).HasMaxLength(45);
        builder.Property(x => x.AvatarUrl).HasMaxLength(2048); builder.Property(x => x.Role).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.IsActive).HasDefaultValue(true); builder.Property(x => x.FailedLoginCount).HasDefaultValue(0);
        builder.HasIndex(x => x.NormalizedEmail).IsUnique(); builder.HasIndex(x => x.NormalizedUsername).IsUnique();
        builder.ToTable("users", table => table.HasCheckConstraint("ck_users_failed_login_count", "failed_login_count >= 0"));
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.SetNull);
    }
}