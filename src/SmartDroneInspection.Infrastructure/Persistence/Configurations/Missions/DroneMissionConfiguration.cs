using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Missions;

public sealed class DroneMissionConfiguration : IEntityTypeConfiguration<DroneMission>
{
    public void Configure(EntityTypeBuilder<DroneMission> builder)
    {
        builder.ConfigureBase("drone_missions"); builder.Property(x => x.SmartDroneHubMissionId); builder.Property(x => x.ExternalStatusCode).HasMaxLength(100);
        builder.Property(x => x.MissionType).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.CreatedVia).HasConversion<string>().HasMaxLength(32);
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
