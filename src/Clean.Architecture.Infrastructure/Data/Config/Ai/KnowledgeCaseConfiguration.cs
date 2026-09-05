using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Ai;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Infrastructure.Data.Config;
using Clean.Architecture.Core.Ai.Enums;

namespace Clean.Architecture.Infrastructure.Data.Config.Ai;

public sealed class KnowledgeCaseConfiguration : IEntityTypeConfiguration<KnowledgeCase>
{
    public void Configure(EntityTypeBuilder<KnowledgeCase> builder)
    {
        builder.ConfigureBase("knowledge_cases");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit();
        builder.Property(x => x.Title).HasMaxLength(300).IsRequired(); builder.Property(x => x.Content).HasColumnType("text").IsRequired(); builder.Property(x => x.Summary).HasColumnType("text");
        builder.Property(x => x.CaseType).HasConversion(SmartEnumStringConverter.Create<KnowledgeCaseType>()).HasMaxLength(40); builder.Property(x => x.Tags).HasColumnType("text[]"); builder.Property(x => x.Language).HasMaxLength(2).IsRequired(); builder.Property(x => x.Source).HasConversion(SmartEnumStringConverter.Create<KnowledgeCaseSource>()).HasMaxLength(32);
        builder.HasIndex(x => new { x.IsPublished, x.CaseType }); builder.HasIndex(x => x.Tags).HasMethod("gin");
        builder.HasOne<Defect>().WithMany().HasForeignKey(x => x.DefectId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.SetNull);
    }
}