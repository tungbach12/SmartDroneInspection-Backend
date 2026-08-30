using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Users;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Planning;

public sealed class NotificationConfiguration : IEntityTypeConfiguration<Notification>
{
    public void Configure(EntityTypeBuilder<Notification> builder)
    {
        builder.ConfigureBase("notifications"); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Message).HasColumnType("text").IsRequired();
        builder.Property(x => x.Type).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.Category).HasMaxLength(50).IsRequired();
        builder.Property(x => x.RefEntityType).HasMaxLength(100); builder.Property(x => x.ActionUrl).HasMaxLength(2048); builder.Property(x => x.DeliveryChannel).HasConversion<string>().HasMaxLength(32);
        builder.Property(x => x.DeliveryStatus).HasConversion<string>().HasMaxLength(32); builder.Property(x => x.IdempotencyKey).HasMaxLength(200).IsRequired();
        builder.HasIndex(x => new { x.UserId, x.IsRead }); builder.HasIndex(x => x.IdempotencyKey).IsUnique();
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
    }
}
