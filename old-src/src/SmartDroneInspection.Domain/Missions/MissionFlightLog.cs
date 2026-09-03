using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Missions;

public class MissionFlightLog : BaseEntity
{
    public Guid DroneMissionId { get; set; }
    public long SequenceNumber { get; set; }
    public FlightLogType LogType { get; set; }
    public int Severity { get; set; }
    public JsonDocument Content { get; set; } = JsonDocument.Parse("{}");
    public DateTime LoggedAt { get; set; }
}
