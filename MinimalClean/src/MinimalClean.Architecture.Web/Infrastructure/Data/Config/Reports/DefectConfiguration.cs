using Microsoft.EntityFrameworkCore;
using MinimalClean.Architecture.Web.Domain.Reports.Enums;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Reports;

public sealed class DefectConfiguration : IEntityTypeConfiguration<Defect>
{
    public void Configure(EntityTypeBuilder<Defect> builder)
    {
        builder.ConfigureBase("defects");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit(); builder.Property(x => x.DefectNumber).HasMaxLength(40); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text").IsRequired(); builder.Property(x => x.Severity).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Category).HasConversion<string>().HasMaxLength(40); builder.Property(x => x.RepairRecommendation).HasColumnType("text"); builder.Property(x => x.RepairPriority).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Status).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.EstimatedRepairCost).HasPrecision(15, 2);
        builder.HasIndex(x => x.DefectNumber).IsUnique(); builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => new { x.AssetId, x.DetectedAt }); builder.ToTable("defects", table => table.HasCheckConstraint("ck_defects_values", "(estimated_repair_cost IS NULL OR estimated_repair_cost >= 0) AND (estimated_repair_hours IS NULL OR estimated_repair_hours >= 0)"));
        builder.HasOne<Asset>().WithMany().HasForeignKey(x => x.AssetId).OnDelete(DeleteBehavior.Restrict); builder.HasOne<InspectionReport>().WithMany().HasForeignKey(x => x.ReportId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<ReportFinding>().WithMany().HasForeignKey(x => x.FindingId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.ConfirmedByUserId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.ResolvedByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}