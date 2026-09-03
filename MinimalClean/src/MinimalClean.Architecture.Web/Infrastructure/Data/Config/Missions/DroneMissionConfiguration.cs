using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Domain.Missions.Enums;
using MinimalClean.Architecture.Web.Domain.Users;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Missions;

public sealed class DroneMissionConfiguration : IEntityTypeConfiguration<DroneMission>
{
    public void Configure(EntityTypeBuilder<DroneMission> builder)
    {
        builder.ConfigureBase("drone_missions");
        builder.Property(x => x.Id).HasVogenConversion();
        builder.Property(x => x.SmartDroneHubMissionId);
        builder.Property(x => x.ExternalStatusCode).HasMaxLength(100);
        builder.Property(x => x.MissionType).HasConversion(x => x.Name, x => MissionType.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.Status).HasConversion(x => x.Name, x => DroneMissionStatus.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.CreatedVia).HasConversion(x => x.Name, x => MissionCreatedVia.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.Notes).HasColumnType("text"); builder.Property(x => x.WeatherConditions).HasColumnType("jsonb"); builder.Property(x => x.Version).HasDefaultValue(1).IsConcurrencyToken(); builder.Property(x => x.LastSyncedAt);
        builder.HasIndex(x => x.SmartDroneHubMissionId).IsUnique(); builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => x.InspectionRequestId);
        builder.ToTable("drone_missions", table =>
        {
            table.HasCheckConstraint("ck_drone_missions_values", "(planned_altitude_meters IS NULL OR planned_altitude_meters >= 0) AND (planned_distance_meters IS NULL OR planned_distance_meters >= 0) AND (total_distance_meters IS NULL OR total_distance_meters >= 0) AND (total_flight_time_seconds IS NULL OR total_flight_time_seconds >= 0) AND (max_altitude_meters IS NULL OR max_altitude_meters >= 0) AND (max_battery_used_percent IS NULL OR max_battery_used_percent BETWEEN 0 AND 100)");
        });
        builder.HasOne<InspectionRequest>().WithMany().HasForeignKey(x => x.InspectionRequestId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.LaunchedByUserId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.CancelledByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}
