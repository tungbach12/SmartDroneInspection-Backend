using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Missions;

public class MissionTelemetry : BaseEntity
{
    public Guid DroneMissionId { get; set; }
    public long SequenceNumber { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double AltitudeMeters { get; set; }
    public double? GroundSpeedMps { get; set; }
    public int BatteryPercent { get; set; }
    public int? SignalStrengthPercent { get; set; }
    public double? HeadingDegrees { get; set; }
    public DateTime RecordedAt { get; set; }
    public DateTime ServerReceivedAt { get; set; }
}
