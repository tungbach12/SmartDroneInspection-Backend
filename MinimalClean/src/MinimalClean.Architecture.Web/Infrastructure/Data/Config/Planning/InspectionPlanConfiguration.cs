using MinimalClean.Architecture.Web.Domain.Planning;
using MinimalClean.Architecture.Web.Domain.Planning.Enums;
using MinimalClean.Architecture.Web.Domain.Users;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Planning;

public sealed class InspectionPlanConfiguration : IEntityTypeConfiguration<InspectionPlan>
{
    public void Configure(EntityTypeBuilder<InspectionPlan> builder)
    {
        builder.ConfigureBase("inspection_plans");
        builder.Property(x => x.Id).HasVogenConversion();
        builder.ConfigureAudit();
        builder.ConfigureSoftDelete();
        builder.Property(x => x.Title).HasMaxLength(250).IsRequired();
        builder.Property(x => x.Description).HasColumnType("text");
        builder.Property(x => x.FrequencyType).HasConversion(x => x.Name, x => FrequencyType.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.Priority).HasConversion(x => x.Name, x => InspectionPlanPriority.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.Status).HasConversion(x => x.Name, x => InspectionPlanStatus.FromName(x)).HasMaxLength(32);
        builder.Property(x => x.FrequencyInterval).HasDefaultValue(1);
        builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => x.NextRunDate);
        builder.ToTable("inspection_plans", table => table.HasCheckConstraint("ck_inspection_plans_frequency_interval", "frequency_interval >= 1"));
        builder.ToTable("inspection_plans", table => table.HasCheckConstraint("ck_inspection_plans_date_range", "end_date IS NULL OR end_date >= start_date"));
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.CreatedByUserId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.ActivatedByUserId).OnDelete(DeleteBehavior.SetNull);
    }
}
