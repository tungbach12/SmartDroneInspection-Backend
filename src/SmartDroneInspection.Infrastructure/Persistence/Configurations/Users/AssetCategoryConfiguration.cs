using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Users;

public sealed class AssetCategoryConfiguration : IEntityTypeConfiguration<AssetCategory>
{
    public void Configure(EntityTypeBuilder<AssetCategory> builder)
    {
        builder.ConfigureBase("asset_categories"); builder.ConfigureSoftDelete();
        builder.Property(x => x.Name).HasMaxLength(150).IsRequired(); builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.IconUrl).HasMaxLength(2048); builder.Property(x => x.SortOrder).HasDefaultValue(0);
        builder.HasIndex(x => x.Name).IsUnique();
        builder.HasOne<AssetCategory>().WithMany().HasForeignKey(x => x.ParentId).OnDelete(DeleteBehavior.Restrict);
    }
}
