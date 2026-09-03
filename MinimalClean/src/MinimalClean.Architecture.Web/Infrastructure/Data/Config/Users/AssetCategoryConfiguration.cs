using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Users;

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