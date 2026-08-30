using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Reports;

public sealed class ReportFindingConfiguration : IEntityTypeConfiguration<ReportFinding>
{
    public void Configure(EntityTypeBuilder<ReportFinding> builder)
    {
        builder.ConfigureBase("report_findings"); builder.Property(x => x.Description).HasColumnType("text").IsRequired(); builder.Property(x => x.Severity).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.LocationNote).HasMaxLength(500); builder.Property(x => x.BoundingBoxJson).HasMaxLength(500); builder.Property(x => x.ConfidenceScore).HasPrecision(5, 4);
        builder.HasIndex(x => new { x.ReportId, x.Severity }); builder.ToTable("report_findings", table => table.HasCheckConstraint("ck_report_findings_confidence", "confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1")); builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.Cascade);
        builder.HasOne<MissionImage>().WithMany().HasForeignKey(x => x.ImageId).OnDelete(DeleteBehavior.SetNull);
    }
}
