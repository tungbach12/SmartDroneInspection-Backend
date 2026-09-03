using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Missions;

public sealed class InspectionRequestConfiguration : IEntityTypeConfiguration<InspectionRequest>
{
    public void Configure(EntityTypeBuilder<InspectionRequest> builder)
    {
        builder.ConfigureBase("inspection_requests"); builder.ConfigureAudit(); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text").IsRequired();
        builder.Property(x => x.Priority).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.LocationOverride).HasMaxLength(500);
        builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => x.MissionCreationKey).IsUnique();
        builder.ToTable("inspection_requests", table => table.HasCheckConstraint("ck_inspection_requests_coordinates", "(latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)"));
        builder.ToTable("inspection_requests", table => table.HasCheckConstraint("ck_inspection_requests_duration", "estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0"));
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.RequestedByUserId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.InspectorId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionPlan>().WithMany().HasForeignKey(x => x.PlanId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.DecidedByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}
