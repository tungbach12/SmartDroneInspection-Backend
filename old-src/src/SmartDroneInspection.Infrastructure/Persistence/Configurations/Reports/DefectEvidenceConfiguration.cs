using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Reports;

public sealed class DefectEvidenceConfiguration : IEntityTypeConfiguration<DefectEvidence>
{
    public void Configure(EntityTypeBuilder<DefectEvidence> builder)
    {
        builder.ConfigureBase("defect_evidence"); builder.Property(x => x.MinioObjectKey).HasMaxLength(1024).IsRequired(); builder.Property(x => x.ThumbnailObjectKey).HasMaxLength(1024); builder.Property(x => x.FileType).HasMaxLength(32).IsRequired(); builder.Property(x => x.MimeType).HasMaxLength(255).IsRequired(); builder.Property(x => x.Caption).HasMaxLength(1000); builder.HasIndex(x => x.MinioObjectKey).IsUnique(); builder.HasIndex(x => x.DefectId); builder.ToTable("defect_evidence", table => table.HasCheckConstraint("ck_defect_evidence_size", "file_size_bytes >= 0")); builder.HasOne<Defect>().WithMany().HasForeignKey(x => x.DefectId).OnDelete(DeleteBehavior.Restrict); builder.HasOne<User>().WithMany().HasForeignKey(x => x.UploadedByUserId).OnDelete(DeleteBehavior.Restrict);
    }
}
