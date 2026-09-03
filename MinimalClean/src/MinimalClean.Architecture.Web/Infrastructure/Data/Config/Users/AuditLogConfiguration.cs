using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data.Config;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config.Users;

public sealed class AuditLogConfiguration : IEntityTypeConfiguration<AuditLog>
{
    public void Configure(EntityTypeBuilder<AuditLog> builder)
    {
        builder.ConfigureBase("audit_logs");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.Action).HasMaxLength(100).IsRequired();
        builder.Property(x => x.EntityType).HasMaxLength(100); builder.Property(x => x.Category).HasMaxLength(50).IsRequired();
        builder.Property(x => x.OldValues).HasColumnType("jsonb"); builder.Property(x => x.NewValues).HasColumnType("jsonb");
        builder.Property(x => x.IpAddress).HasMaxLength(45); builder.Property(x => x.UserAgent).HasMaxLength(1000);
        builder.Property(x => x.CorrelationId).HasMaxLength(100); builder.Property(x => x.OccurredAt).IsRequired();
        builder.HasIndex(x => new { x.Category, x.OccurredAt }); builder.HasIndex(x => x.OccurredAt);
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.SetNull);
    }
}