using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Planning;

public class Notification : BaseEntity
{
    public Guid UserId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public NotificationType Type { get; set; }
    public string Category { get; set; } = string.Empty;
    public string? RefEntityType { get; set; }
    public Guid? RefEntityId { get; set; }
    public string? ActionUrl { get; set; }
    public bool IsRead { get; set; }
    public DateTime? ReadAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public DateTime? SentAt { get; set; }
    public DeliveryChannel DeliveryChannel { get; set; } = DeliveryChannel.InApp;
    public DeliveryStatus DeliveryStatus { get; set; } = DeliveryStatus.Pending;
    public string IdempotencyKey { get; set; } = string.Empty;
}
