using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Assets;

public sealed class AssetLifecycleLogConfiguration : IEntityTypeConfiguration<AssetLifecycleLog>
{
    public void Configure(EntityTypeBuilder<AssetLifecycleLog> builder)
    {
        builder.ConfigureBase("asset_lifecycle_logs"); builder.Property(x => x.FromStatus).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.ToStatus).HasConversion<string>().HasMaxLength(32).IsRequired(); builder.Property(x => x.ChangedAt).IsRequired();
        builder.Property(x => x.Reason).HasMaxLength(500); builder.Property(x => x.Note).HasColumnType("text"); builder.HasIndex(x => new { x.AssetId, x.ChangedAt });
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.ChangedBy).OnDelete(DeleteBehavior.SetNull);
    }
}
