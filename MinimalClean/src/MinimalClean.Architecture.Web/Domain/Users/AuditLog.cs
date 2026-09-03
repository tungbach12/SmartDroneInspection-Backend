using Ardalis.GuardClauses;
using System.Text.Json;

namespace MinimalClean.Architecture.Web.Domain.Users;

public class AuditLog : EntityBase<AuditLog, AuditLogId>, IAggregateRoot
{
    private AuditLog() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public AuditLog(
        string action = default!,
        string category = default!,
        DateTime occurredAt = default,
        Guid? userId = default!,
        string? entityType = default!,
        Guid? entityId = default!,
        JsonDocument? oldValues = default!,
        JsonDocument? newValues = default!,
        string? ipAddress = default!,
        string? userAgent = default!,
        string? correlationId = default!)  
    {
        Action = Guard.Against.NullOrWhiteSpace(action, nameof(action));
        Category = Guard.Against.NullOrWhiteSpace(category, nameof(category));
        OccurredAt = occurredAt;
        UserId = userId;
        EntityType = entityType;
        EntityId = entityId;
        OldValues = oldValues;
        NewValues = newValues;
        IpAddress = ipAddress;
        UserAgent = userAgent;
        CorrelationId = correlationId;
    }

    public Guid? UserId { get; private set; }
    public string Action { get; private set; } = string.Empty;
    public string? EntityType { get; private set; }
    public Guid? EntityId { get; private set; }
    public string Category { get; private set; } = string.Empty;
    public JsonDocument? OldValues { get; private set; }
    public JsonDocument? NewValues { get; private set; }
    public string? IpAddress { get; private set; }
    public string? UserAgent { get; private set; }
    public string? CorrelationId { get; private set; }
    public DateTime OccurredAt { get; private set; }

    public AuditLog UpdateUserId(Guid? newUserId)
    {
        UserId = newUserId;
        return this;
    }

    public AuditLog UpdateAction(string newAction)
    {
        Action = Guard.Against.NullOrWhiteSpace(newAction, nameof(newAction));
        return this;
    }

    public AuditLog UpdateEntityType(string? newEntityType)
    {
        EntityType = newEntityType;
        return this;
    }

    public AuditLog UpdateEntityId(Guid? newEntityId)
    {
        EntityId = newEntityId;
        return this;
    }

    public AuditLog UpdateCategory(string newCategory)
    {
        Category = Guard.Against.NullOrWhiteSpace(newCategory, nameof(newCategory));
        return this;
    }

    public AuditLog UpdateOldValues(JsonDocument? newOldValues)
    {
        OldValues = newOldValues;
        return this;
    }

    public AuditLog UpdateNewValues(JsonDocument? newNewValues)
    {
        NewValues = newNewValues;
        return this;
    }

    public AuditLog UpdateIpAddress(string? newIpAddress)
    {
        IpAddress = newIpAddress;
        return this;
    }

    public AuditLog UpdateUserAgent(string? newUserAgent)
    {
        UserAgent = newUserAgent;
        return this;
    }

    public AuditLog UpdateCorrelationId(string? newCorrelationId)
    {
        CorrelationId = newCorrelationId;
        return this;
    }

    public AuditLog UpdateOccurredAt(DateTime newOccurredAt)
    {
        OccurredAt = newOccurredAt;
        return this;
    }

}
