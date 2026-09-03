using System.Text.Json;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Domain.Ai;

public class AiAnalysisJob : BaseEntity
{
    public Guid? MissionImageId { get; set; }
    public Guid? DefectId { get; set; }
    public Guid? ReportId { get; set; }
    public AiJobType JobType { get; set; }
    public AiJobStatus Status { get; set; } = AiJobStatus.Queued;
    public int Priority { get; set; } = 5;
    public JsonDocument? InputPayload { get; set; }
    public JsonDocument? Result { get; set; }
    public decimal? Confidence { get; set; }
    public string? ModelName { get; set; }
    public string? ModelVersion { get; set; }
    public int? PromptTokens { get; set; }
    public int? CompletionTokens { get; set; }
    public decimal? TotalCostUsd { get; set; }
    public int? LatencyMs { get; set; }
    public string? ErrorCode { get; set; }
    public string? ErrorMessage { get; set; }
    public Guid? RequestedByUserId { get; set; }
    public DateTime QueuedAt { get; set; }
    public DateTime? StartedAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public int RetryCount { get; set; }
    public int MaxRetries { get; set; } = 3;
}
