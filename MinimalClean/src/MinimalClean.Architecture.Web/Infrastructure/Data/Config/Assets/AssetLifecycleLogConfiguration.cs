using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Assets;

public sealed class AssetLifecycleLogConfiguration : IEntityTypeConfiguration<AssetLifecycleLog>
{
    public void Configure(EntityTypeBuilder<AssetLifecycleLog> builder)
    {
        builder.ConfigureBase("asset_lifecycle_logs");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.FromStatus).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.ToStatus).HasConversion<string>().HasMaxLength(32).IsRequired(); builder.Property(x => x.ChangedAt).IsRequired();
        builder.Property(x => x.Reason).HasMaxLength(500); builder.Property(x => x.Note).HasColumnType("text"); builder.HasIndex(x => new { x.AssetId, x.ChangedAt });
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.ChangedBy).OnDelete(DeleteBehavior.SetNull);
    }
}