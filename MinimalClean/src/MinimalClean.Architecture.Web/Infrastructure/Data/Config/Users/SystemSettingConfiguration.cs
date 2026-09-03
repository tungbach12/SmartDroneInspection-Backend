using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Users;

public sealed class SystemSettingConfiguration : IEntityTypeConfiguration<SystemSetting>
{
    public void Configure(EntityTypeBuilder<SystemSetting> builder)
    {
        builder.ConfigureBase("system_settings");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit();
        builder.Property(x => x.Key).HasMaxLength(150).IsRequired(); builder.Property(x => x.Value).HasColumnType("jsonb").IsRequired();
        builder.Property(x => x.Description).HasColumnType("text"); builder.Property(x => x.Version).HasDefaultValue(1).IsConcurrencyToken();
        builder.HasIndex(x => x.Key).IsUnique(); builder.HasIndex(x => x.Version);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.UpdatedBy).OnDelete(DeleteBehavior.SetNull);
    }
}