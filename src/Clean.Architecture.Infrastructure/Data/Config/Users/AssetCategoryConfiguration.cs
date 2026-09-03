using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;

namespace Clean.Architecture.Infrastructure.Data.Config.Users;

public sealed class AssetCategoryConfiguration : IEntityTypeConfiguration<AssetCategory>
{
    public void Configure(EntityTypeBuilder<AssetCategory> builder)
    {
        builder.ConfigureBase("asset_categories");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureSoftDelete();
        builder.Property(x => x.Name).HasMaxLength(150).IsRequired(); builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.IconUrl).HasMaxLength(2048); builder.Property(x => x.SortOrder).HasDefaultValue(0);
        builder.HasIndex(x => x.Name).IsUnique();
        builder.HasOne<AssetCategory>().WithMany().HasForeignKey(x => x.ParentId).OnDelete(DeleteBehavior.Restrict);
    }
}