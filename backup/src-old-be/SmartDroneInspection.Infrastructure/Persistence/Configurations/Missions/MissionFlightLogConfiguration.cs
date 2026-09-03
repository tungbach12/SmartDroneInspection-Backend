using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Missions;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Missions;

public sealed class MissionFlightLogConfiguration : IEntityTypeConfiguration<MissionFlightLog>
{
    public void Configure(EntityTypeBuilder<MissionFlightLog> builder)
    {
        builder.ConfigureBase("mission_flight_logs"); builder.Property(x => x.LogType).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Content).HasColumnType("jsonb").IsRequired();
        builder.HasIndex(x => new { x.DroneMissionId, x.SequenceNumber }).IsUnique(); builder.HasIndex(x => new { x.DroneMissionId, x.LoggedAt });
        builder.ToTable("mission_flight_logs", table => table.HasCheckConstraint("ck_mission_flight_logs_values", "sequence_number >= 0 AND severity BETWEEN 1 AND 5"));
        builder.HasOne<DroneMission>().WithMany().HasForeignKey(x => x.DroneMissionId).OnDelete(DeleteBehavior.Cascade);
    }
}
