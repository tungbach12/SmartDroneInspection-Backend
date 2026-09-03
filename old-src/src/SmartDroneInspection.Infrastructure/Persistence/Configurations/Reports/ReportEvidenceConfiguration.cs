using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Reports;

public sealed class ReportEvidenceConfiguration : IEntityTypeConfiguration<ReportEvidence>
{
    public void Configure(EntityTypeBuilder<ReportEvidence> builder)
    {
        builder.ConfigureBase("report_evidence"); builder.ConfigureSoftDelete(); builder.Property(x => x.MinioObjectKey).HasMaxLength(1024).IsRequired(); builder.Property(x => x.ThumbnailObjectKey).HasMaxLength(1024); builder.Property(x => x.FileType).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.MimeType).HasMaxLength(255).IsRequired(); builder.Property(x => x.Caption).HasMaxLength(1000); builder.Property(x => x.UploadedAt).IsRequired();
        builder.HasIndex(x => x.MinioObjectKey).IsUnique(); builder.HasIndex(x => x.ReportId); builder.ToTable("report_evidence", table => table.HasCheckConstraint("ck_report_evidence_size", "file_size_bytes >= 0"));
        builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.Restrict); builder.HasOne<User>().WithMany().HasForeignKey(x => x.UploadedByUserId).OnDelete(DeleteBehavior.Restrict);
    }
}
