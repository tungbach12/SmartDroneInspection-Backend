using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Missions;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Missions;

public sealed class MissionTelemetryConfiguration : IEntityTypeConfiguration<MissionTelemetry>
{
    public void Configure(EntityTypeBuilder<MissionTelemetry> builder)
    {
        builder.ConfigureBase("mission_telemetry");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.ServerReceivedAt).IsRequired(); builder.HasKey(x => x.Id);
        builder.HasIndex(x => new { x.DroneMissionId, x.SequenceNumber }).IsUnique(); builder.HasIndex(x => new { x.DroneMissionId, x.RecordedAt });
        builder.ToTable("mission_telemetry", table =>
        {
            table.HasCheckConstraint("ck_mission_telemetry_values", "sequence_number >= 0 AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND altitude_meters >= 0 AND battery_percent BETWEEN 0 AND 100 AND (ground_speed_mps IS NULL OR ground_speed_mps >= 0) AND (signal_strength_percent IS NULL OR signal_strength_percent BETWEEN 0 AND 100) AND (heading_degrees IS NULL OR heading_degrees BETWEEN 0 AND 360)");
        });
        builder.HasOne<DroneMission>().WithMany().HasForeignKey(x => x.DroneMissionId).OnDelete(DeleteBehavior.Cascade);
    }
}