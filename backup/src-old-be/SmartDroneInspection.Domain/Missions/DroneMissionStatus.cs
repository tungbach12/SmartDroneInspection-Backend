namespace SmartDroneInspection.Domain.Missions;

public enum DroneMissionStatus
{
    Created,
    Queued,
    InFlight,
    Paused,
    Completed,
    Failed,
    Aborted,
    Cancelled,
}
