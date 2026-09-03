using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Planning;

public sealed class InspectionScheduleConfiguration : IEntityTypeConfiguration<InspectionSchedule>
{
    public void Configure(EntityTypeBuilder<InspectionSchedule> builder)
    {
        builder.ConfigureBase("inspection_schedules"); builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.CancelledReason).HasMaxLength(500); builder.HasIndex(x => new { x.AssetId, x.ScheduledDate }); builder.HasIndex(x => new { x.InspectorId, x.ScheduledDate });
        builder.ToTable("inspection_schedules", table => table.HasCheckConstraint("ck_inspection_schedules_date_range", "scheduled_end_date IS NULL OR scheduled_end_date >= scheduled_date"));
        builder.HasOne<InspectionPlan>().WithMany().HasForeignKey(x => x.PlanId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.InspectorId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.AssignedByUserId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionSchedule>().WithMany().HasForeignKey(x => x.RescheduledFromId).OnDelete(DeleteBehavior.SetNull);
    }
}
