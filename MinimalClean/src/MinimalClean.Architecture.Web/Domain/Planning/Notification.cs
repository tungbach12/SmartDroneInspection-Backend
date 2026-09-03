using Ardalis.GuardClauses;
using MinimalClean.Architecture.Web.Domain.Planning.Enums;

namespace MinimalClean.Architecture.Web.Domain.Planning;

public class Notification : EntityBase<Notification, NotificationId>, IAggregateRoot
{
    private Notification() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public Notification(
        Guid userId = default,
        string title = default!,
        string message = default!,
        NotificationType type = default!,
        string category = default!,
        bool isRead = default,
        string idempotencyKey = default!,
        string? refEntityType = default!,
        Guid? refEntityId = default!,
        string? actionUrl = default!,
        DateTime? readAt = default!,
        DateTime? expiresAt = default!,
        DateTime? sentAt = default!,
        DeliveryChannel deliveryChannel = default!,
        DeliveryStatus deliveryStatus = default!)  
    {
        UserId = Guard.Against.Default(userId, nameof(userId));
        Title = Guard.Against.NullOrWhiteSpace(title, nameof(title));
        Message = Guard.Against.NullOrWhiteSpace(message, nameof(message));
        Type = type;
        Category = Guard.Against.NullOrWhiteSpace(category, nameof(category));
        IsRead = isRead;
        IdempotencyKey = Guard.Against.NullOrWhiteSpace(idempotencyKey, nameof(idempotencyKey));
        RefEntityType = refEntityType;
        RefEntityId = refEntityId;
        ActionUrl = actionUrl;
        ReadAt = readAt;
        ExpiresAt = expiresAt;
        SentAt = sentAt;
        DeliveryChannel = deliveryChannel;
        DeliveryStatus = deliveryStatus;
    }

    public Guid UserId { get; private set; }
    public string Title { get; private set; } = string.Empty;
    public string Message { get; private set; } = string.Empty;
    public NotificationType Type { get; private set; } = default!;
    public string Category { get; private set; } = string.Empty;
    public string? RefEntityType { get; private set; }
    public Guid? RefEntityId { get; private set; }
    public string? ActionUrl { get; private set; }
    public bool IsRead { get; private set; }
    public DateTime? ReadAt { get; private set; }
    public DateTime? ExpiresAt { get; private set; }
    public DateTime? SentAt { get; private set; }
    public DeliveryChannel DeliveryChannel { get; private set; } = DeliveryChannel.InApp;
    public DeliveryStatus DeliveryStatus { get; private set; } = DeliveryStatus.Pending;
    public string IdempotencyKey { get; private set; } = string.Empty;

    public Notification UpdateUserId(Guid newUserId)
    {
        UserId = newUserId;
        return this;
    }

    public Notification UpdateTitle(string newTitle)
    {
        Title = Guard.Against.NullOrWhiteSpace(newTitle, nameof(newTitle));
        return this;
    }

    public Notification UpdateMessage(string newMessage)
    {
        Message = Guard.Against.NullOrWhiteSpace(newMessage, nameof(newMessage));
        return this;
    }

    public Notification UpdateType(NotificationType newType)
    {
        Type = newType;
        return this;
    }

    public Notification UpdateCategory(string newCategory)
    {
        Category = Guard.Against.NullOrWhiteSpace(newCategory, nameof(newCategory));
        return this;
    }

    public Notification UpdateRefEntityType(string? newRefEntityType)
    {
        RefEntityType = newRefEntityType;
        return this;
    }

    public Notification UpdateRefEntityId(Guid? newRefEntityId)
    {
        RefEntityId = newRefEntityId;
        return this;
    }

    public Notification UpdateActionUrl(string? newActionUrl)
    {
        ActionUrl = newActionUrl;
        return this;
    }

    public Notification UpdateIsRead(bool newIsRead)
    {
        IsRead = newIsRead;
        return this;
    }

    public Notification UpdateReadAt(DateTime? newReadAt)
    {
        ReadAt = newReadAt;
        return this;
    }

    public Notification UpdateExpiresAt(DateTime? newExpiresAt)
    {
        ExpiresAt = newExpiresAt;
        return this;
    }

    public Notification UpdateSentAt(DateTime? newSentAt)
    {
        SentAt = newSentAt;
        return this;
    }

    public Notification UpdateDeliveryChannel(DeliveryChannel newDeliveryChannel)
    {
        DeliveryChannel = newDeliveryChannel;
        return this;
    }

    public Notification UpdateDeliveryStatus(DeliveryStatus newDeliveryStatus)
    {
        DeliveryStatus = newDeliveryStatus;
        return this;
    }

    public Notification UpdateIdempotencyKey(string newIdempotencyKey)
    {
        IdempotencyKey = Guard.Against.NullOrWhiteSpace(newIdempotencyKey, nameof(newIdempotencyKey));
        return this;
    }

}
