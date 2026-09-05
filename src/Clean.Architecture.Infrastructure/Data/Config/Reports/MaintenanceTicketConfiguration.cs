using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;
using Clean.Architecture.Core.Reports.Enums;

namespace Clean.Architecture.Infrastructure.Data.Config.Reports;

public sealed class MaintenanceTicketConfiguration : IEntityTypeConfiguration<MaintenanceTicket>
{
    public void Configure(EntityTypeBuilder<MaintenanceTicket> builder)
    {
        builder.ConfigureBase("maintenance_tickets");
        builder.Property(x => x.Id).HasVogenConversion(); builder.ConfigureAudit(); builder.Property(x => x.TicketNumber).HasMaxLength(40); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Description).HasColumnType("text").IsRequired(); builder.Property(x => x.Priority).HasConversion(SmartEnumStringConverter.Create<TicketPriority>()).HasMaxLength(32); builder.Property(x => x.Status).HasConversion(SmartEnumStringConverter.Create<TicketStatus>()).HasMaxLength(32); builder.Property(x => x.ResolutionNotes).HasColumnType("text"); builder.Property(x => x.EstimatedCost).HasPrecision(15, 2); builder.Property(x => x.ActualCost).HasPrecision(15, 2);
        builder.HasIndex(x => x.TicketNumber).IsUnique(); builder.HasIndex(x => new { x.OrganizationId, x.Status }); builder.HasIndex(x => new { x.AssignedToUserId, x.Status }); builder.ToTable("maintenance_tickets", table => table.HasCheckConstraint("ck_maintenance_tickets_costs", "(estimated_cost IS NULL OR estimated_cost >= 0) AND (actual_cost IS NULL OR actual_cost >= 0)"));
        builder.HasOne<Organization>().WithMany().HasForeignKey(x => x.OrganizationId).OnDelete(DeleteBehavior.Restrict);
        builder.HasOne<Defect>().WithMany().HasForeignKey(x => x.DefectId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<InspectionRequest>().WithMany().HasForeignKey(x => x.RequestId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.AssignedToUserId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.AssignedByUserId).OnDelete(DeleteBehavior.SetNull);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.CreatedByUserId).OnDelete(DeleteBehavior.Restrict);
    }
}