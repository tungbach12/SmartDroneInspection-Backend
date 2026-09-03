using Ardalis.GuardClauses;
using Clean.Architecture.Core.Missions.Enums;
using System.Text.Json;

namespace Clean.Architecture.Core.Missions;

public class MissionFlightLog : EntityBase<MissionFlightLog, MissionFlightLogId>, IAggregateRoot
{
    private MissionFlightLog() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public MissionFlightLog(
        Guid droneMissionId = default,
        long sequenceNumber = default,
        FlightLogType logType = default!,
        int severity = default,
        DateTime loggedAt = default,
        JsonDocument content = default!)  
    {
        DroneMissionId = Guard.Against.Default(droneMissionId, nameof(droneMissionId));
        SequenceNumber = sequenceNumber;
        LogType = logType;
        Severity = severity;
        LoggedAt = loggedAt;
        Content = content;
    }

    public Guid DroneMissionId { get; private set; }
    public long SequenceNumber { get; private set; }
    public FlightLogType LogType { get; private set; } = default!;
    public int Severity { get; private set; }
    public JsonDocument Content { get; private set; } = JsonDocument.Parse("{}");
    public DateTime LoggedAt { get; private set; }

    public MissionFlightLog UpdateDroneMissionId(Guid newDroneMissionId)
    {
        DroneMissionId = newDroneMissionId;
        return this;
    }

    public MissionFlightLog UpdateSequenceNumber(long newSequenceNumber)
    {
        SequenceNumber = newSequenceNumber;
        return this;
    }

    public MissionFlightLog UpdateLogType(FlightLogType newLogType)
    {
        LogType = newLogType;
        return this;
    }

    public MissionFlightLog UpdateSeverity(int newSeverity)
    {
        Severity = newSeverity;
        return this;
    }

    public MissionFlightLog UpdateContent(JsonDocument newContent)
    {
        Content = newContent;
        return this;
    }

    public MissionFlightLog UpdateLoggedAt(DateTime newLoggedAt)
    {
        LoggedAt = newLoggedAt;
        return this;
    }

}
