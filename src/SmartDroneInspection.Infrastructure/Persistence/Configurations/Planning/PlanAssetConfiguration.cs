using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Planning;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Planning;

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
