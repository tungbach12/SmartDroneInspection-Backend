using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Core.Reports.Enums;

namespace Clean.Architecture.Infrastructure.Data.Config.Reports;

public sealed class TicketHistoryConfiguration : IEntityTypeConfiguration<TicketHistory>
{
    public void Configure(EntityTypeBuilder<TicketHistory> builder)
    {
        builder.ConfigureBase("ticket_history");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.FromStatus).HasConversion(SmartEnumStringConverter.CreateNullable<TicketStatus>()).HasMaxLength(32); builder.Property(x => x.ToStatus).HasConversion(SmartEnumStringConverter.Create<TicketStatus>()).HasMaxLength(32); builder.Property(x => x.Comment).HasMaxLength(1000); builder.HasIndex(x => new { x.TicketId, x.ChangedAt }); builder.ToTable("ticket_history", table => table.HasCheckConstraint("ck_ticket_history_time", "time_spent_minutes IS NULL OR time_spent_minutes >= 0")); builder.HasOne<MaintenanceTicket>().WithMany().HasForeignKey(x => x.TicketId).OnDelete(DeleteBehavior.Cascade); builder.HasOne<User>().WithMany().HasForeignKey(x => x.ChangedByUserId).OnDelete(DeleteBehavior.Restrict);
    }
}