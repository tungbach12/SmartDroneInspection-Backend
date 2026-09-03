using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.Core.Planning;

namespace Clean.Architecture.Infrastructure.Data.Config.Planning;

public sealed class PlanAssetConfiguration : IEntityTypeConfiguration<PlanAsset>
{
    public void Configure(EntityTypeBuilder<PlanAsset> builder)
    {
        builder.ToTable("plan_assets"); builder.HasKey(x => new { x.PlanId, x.AssetId }); builder.Property(x => x.SortOrder).HasDefaultValue(0);
        builder.HasIndex(x => x.AssetId);
        builder.HasOne<InspectionPlan>().WithMany().HasForeignKey(x => x.PlanId).OnDelete(DeleteBehavior.Cascade);
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
    }
}