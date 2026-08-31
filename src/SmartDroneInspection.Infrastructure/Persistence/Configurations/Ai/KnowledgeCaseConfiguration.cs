using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Ai;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Ai;

public sealed class KnowledgeCaseConfiguration : IEntityTypeConfiguration<KnowledgeCase>
{
    public void Configure(EntityTypeBuilder<KnowledgeCase> builder)
    {
        builder.ConfigureBase("knowledge_cases"); builder.ConfigureAudit();
        builder.Property(x => x.Title).HasMaxLength(300).IsRequired(); builder.Property(x => x.Content).HasColumnType("text").IsRequired(); builder.Property(x => x.Summary).HasColumnType("text");
        builder.Property(x => x.CaseType).HasConversion<string>().HasMaxLength(40); builder.Property(x => x.Tags).HasColumnType("text[]"); builder.Property(x => x.Language).HasMaxLength(2).IsRequired(); builder.Property(x => x.Source).HasConversion<string>().HasMaxLength(32);
        builder.HasIndex(x => new { x.IsPublished, x.CaseType }); builder.HasIndex(x => x.Tags).HasMethod("gin");
        builder.HasOne<Defect>().WithMany().HasForeignKey(x => x.DefectId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.SetNull);
    }
}
