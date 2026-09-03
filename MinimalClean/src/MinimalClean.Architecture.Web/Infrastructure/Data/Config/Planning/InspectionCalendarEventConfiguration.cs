using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Planning;
using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Domain.Users;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Planning;

public sealed class InspectionCalendarEventConfiguration : IEntityTypeConfiguration<InspectionCalendarEvent>
{
    public void Configure(EntityTypeBuilder<InspectionCalendarEvent> builder)
    {
        builder.ConfigureBase("inspection_calendar_events");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.Location).HasMaxLength(500); builder.Property(x => x.RecurrenceRule).HasMaxLength(1000); builder.HasIndex(x => x.EventDate);
        builder.ToTable("inspection_calendar_events", table => table.HasCheckConstraint("ck_inspection_calendar_events_date_range", "end_date IS NULL OR end_date >= event_date"));
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.CreatedByUserId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<InspectionPlan>().WithMany().HasForeignKey(x => x.PlanId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionRequest>().WithMany().HasForeignKey(x => x.RequestId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionSchedule>().WithMany().HasForeignKey(x => x.ScheduleId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionCalendarEvent>().WithMany().HasForeignKey(x => x.RecurrenceParentId).OnDelete(DeleteBehavior.SetNull);
    }
}