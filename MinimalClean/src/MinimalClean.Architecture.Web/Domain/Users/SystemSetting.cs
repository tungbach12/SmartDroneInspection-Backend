using MinimalClean.Architecture.Web.Domain.Common;
using Ardalis.GuardClauses;
using System.Text.Json;

namespace MinimalClean.Architecture.Web.Domain.Users;

public class SystemSetting : EntityBase<SystemSetting, SystemSettingId>, IAuditable, IHasVersion, IAggregateRoot
{
    private SystemSetting() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public SystemSetting(
        string key = default!,
        JsonDocument value = default!,
        string? description = default!)  
    {
        Key = Guard.Against.NullOrWhiteSpace(key, nameof(key));
        Value = value;
        Description = description;
    }

    public string Key { get; private set; } = string.Empty;
    public JsonDocument Value { get; private set; } = JsonDocument.Parse("null");
    public string? Description { get; private set; }
    public Guid? UpdatedBy { get; set; }
    public int Version { get; set; } = 1;
    public Guid? CreatedBy { get; set; }

    public SystemSetting UpdateKey(string newKey)
    {
        Key = Guard.Against.NullOrWhiteSpace(newKey, nameof(newKey));
        return this;
    }

    public SystemSetting UpdateValue(JsonDocument newValue)
    {
        Value = newValue;
        return this;
    }

    public SystemSetting UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

}
