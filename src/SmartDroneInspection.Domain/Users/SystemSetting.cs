using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Users;

public class SystemSetting : BaseEntity, IAuditable
{
    public string Key { get; set; } = string.Empty;
    public JsonDocument Value { get; set; } = JsonDocument.Parse("null");
    public string? Description { get; set; }
    public Guid? UpdatedBy { get; set; }
    public int Version { get; set; } = 1;
    public Guid? CreatedBy { get; set; }
}
