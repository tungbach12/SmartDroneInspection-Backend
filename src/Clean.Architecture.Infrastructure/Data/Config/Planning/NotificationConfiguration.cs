using Microsoft.EntityFrameworkCore;
using Clean.Architecture.Core.Planning.Enums;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Clean.Architecture.Core.Planning;
using Clean.Architecture.Core.Users;
using Clean.Architecture.Infrastructure.Data.Config;

namespace Clean.Architecture.Infrastructure.Data.Config.Planning;

public sealed class NotificationConfiguration : IEntityTypeConfiguration<Notification>
{
    public void Configure(EntityTypeBuilder<Notification> builder)
    {
        builder.ConfigureBase("notifications");
        builder.Property(x => x.Id).HasVogenConversion(); builder.Property(x => x.Title).HasMaxLength(250).IsRequired(); builder.Property(x => x.Message).HasColumnType("text").IsRequired();
        builder.Property(x => x.Type).HasConversion(x => x.Name, x => NotificationType.FromName(x)).HasMaxLength(32); builder.Property(x => x.Category).HasMaxLength(50).IsRequired();
        builder.Property(x => x.RefEntityType).HasMaxLength(100); builder.Property(x => x.ActionUrl).HasMaxLength(2048); builder.Property(x => x.DeliveryChannel).HasConversion(SmartEnumStringConverter.Create<DeliveryChannel>()).HasMaxLength(32);
        builder.Property(x => x.DeliveryStatus).HasConversion(x => x.Name, x => DeliveryStatus.FromName(x)).HasMaxLength(32); builder.Property(x => x.IdempotencyKey).HasMaxLength(200).IsRequired();
        builder.HasIndex(x => new { x.UserId, x.IsRead }); builder.HasIndex(x => x.IdempotencyKey).IsUnique();
        builder.HasOne<User>().WithMany().HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
    }
}