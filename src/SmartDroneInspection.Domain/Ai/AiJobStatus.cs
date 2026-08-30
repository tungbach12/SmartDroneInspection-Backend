namespace SmartDroneInspection.Domain.Ai;

public enum AiJobStatus
{
    Queued,
    Processing,
    Completed,
    Failed,
    Timeout,
    Cancelled,
}
