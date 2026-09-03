using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Ai;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;

namespace Clean.Architecture.Infrastructure.Data.Config.Ai;

public sealed class AiAnalysisJobConfiguration : IEntityTypeConfiguration<AiAnalysisJob>
{
    public void Configure(EntityTypeBuilder<AiAnalysisJob> builder)
    {
        builder.ConfigureBase("ai_analysis_jobs");
        builder.Property(x => x.Id).HasVogenConversion();
        builder.Property(x => x.JobType).HasConversion<string>().HasMaxLength(40);
        builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.InputPayload).HasColumnType("jsonb");
        builder.Property(x => x.Result).HasColumnType("jsonb");
        builder.Property(x => x.Confidence).HasPrecision(5, 4);
        builder.Property(x => x.ModelName).HasMaxLength(150);
        builder.Property(x => x.ModelVersion).HasMaxLength(100);
        builder.Property(x => x.TotalCostUsd).HasPrecision(10, 6);
        builder.Property(x => x.ErrorCode).HasMaxLength(100);
        builder.Property(x => x.ErrorMessage).HasMaxLength(2000);
        builder.HasIndex(x => new { x.Status, x.Priority, x.QueuedAt });
        builder.HasIndex(x => x.MissionImageId);
        builder.HasIndex(x => x.DefectId);
        builder.HasIndex(x => x.ReportId);
        builder.ToTable("ai_analysis_jobs", table =>
        {
            table.HasCheckConstraint("ck_ai_jobs_priority", "priority BETWEEN 1 AND 10");
            table.HasCheckConstraint("ck_ai_jobs_retries", "retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries");
            table.HasCheckConstraint("ck_ai_jobs_confidence", "confidence IS NULL OR confidence BETWEEN 0 AND 1");
            table.HasCheckConstraint("ck_ai_jobs_one_target", "num_nonnulls(mission_image_id, defect_id, report_id) = 1");
        });
        builder.HasOne<MissionImage>().WithMany().HasForeignKey(x => x.MissionImageId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<Defect>().WithMany().HasForeignKey(x => x.DefectId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.RequestedByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}