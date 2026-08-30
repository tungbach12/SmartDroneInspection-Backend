using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Reports;

public sealed class InspectionReportConfiguration : IEntityTypeConfiguration<InspectionReport>
{
    public void Configure(EntityTypeBuilder<InspectionReport> builder)
    {
        builder.ConfigureBase("inspection_reports"); builder.ConfigureAudit(); builder.ConfigureSoftDelete();
        builder.Property(x => x.ReportNumber).HasMaxLength(40); builder.Property(x => x.Title).HasMaxLength(250).IsRequired();
        builder.Property(x => x.Summary).HasColumnType("text"); builder.Property(x => x.SummaryModelVersion).HasMaxLength(100); builder.Property(x => x.Findings).HasColumnType("text").IsRequired(); builder.Property(x => x.Recommendations).HasColumnType("text");
        builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.RejectReason).HasMaxLength(1000); builder.Property(x => x.ReviewComment).HasMaxLength(2000); builder.Property(x => x.Version).HasDefaultValue(1);
        builder.HasIndex(x => x.ReportNumber).IsUnique(); builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => x.InspectionRequestId).IsUnique();
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<InspectionRequest>().WithMany().HasForeignKey(x => x.InspectionRequestId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<DroneMission>().WithMany().HasForeignKey(x => x.MissionId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.InspectorId).OnDelete(DeleteBehavior.Restrict); builder.HasOne<User>().WithMany().HasForeignKey(x => x.ReviewedByUserId).OnDelete(DeleteBehavior.SetNull); builder.HasOne<User>().WithMany().HasForeignKey(x => x.RejectedByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}
