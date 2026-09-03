using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Reports;

public sealed class TicketHistoryConfiguration : IEntityTypeConfiguration<TicketHistory>
{
    public void Configure(EntityTypeBuilder<TicketHistory> builder)
    {
        builder.ConfigureBase("ticket_history"); builder.Property(x => x.FromStatus).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.ToStatus).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Comment).HasMaxLength(1000); builder.HasIndex(x => new { x.TicketId, x.ChangedAt }); builder.ToTable("ticket_history", table => table.HasCheckConstraint("ck_ticket_history_time", "time_spent_minutes IS NULL OR time_spent_minutes >= 0")); builder.HasOne<MaintenanceTicket>().WithMany().HasForeignKey(x => x.TicketId).OnDelete(DeleteBehavior.Cascade); builder.HasOne<User>().WithMany().HasForeignKey(x => x.ChangedByUserId).OnDelete(DeleteBehavior.Restrict);
    }
}
