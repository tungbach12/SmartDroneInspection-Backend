using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Planning;

public sealed class InspectionCalendarEventConfiguration : IEntityTypeConfiguration<InspectionCalendarEvent>
{
    public void Configure(EntityTypeBuilder<InspectionCalendarEvent> builder)
    {
        builder.ConfigureBase("inspection_calendar_events"); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.Location).HasMaxLength(500); builder.Property(x => x.RecurrenceRule).HasMaxLength(1000); builder.HasIndex(x => x.EventDate);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.CreatedByUserId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<InspectionPlan>().WithMany().HasForeignKey(x => x.PlanId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionRequest>().WithMany().HasForeignKey(x => x.RequestId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionSchedule>().WithMany().HasForeignKey(x => x.ScheduleId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionCalendarEvent>().WithMany().HasForeignKey(x => x.RecurrenceParentId).OnDelete(DeleteBehavior.SetNull);
    }
}
