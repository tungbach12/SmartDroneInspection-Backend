using Ardalis.GuardClauses;
using Clean.Architecture.Core.Ai.Enums;
using System.Text.Json;

namespace Clean.Architecture.Core.Ai;

public class AiAnalysisJob : EntityBase<AiAnalysisJob, AiAnalysisJobId>, IAggregateRoot
{
    private AiAnalysisJob() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public AiAnalysisJob(
        AiJobType jobType = default!,
        DateTime queuedAt = default,
        int retryCount = default,
        Guid? missionImageId = default!,
        Guid? defectId = default!,
        Guid? reportId = default!,
        AiJobStatus status = default!,
        int priority = 5,
        JsonDocument? inputPayload = default!,
        JsonDocument? result = default!,
        decimal? confidence = default!,
        string? modelName = default!,
        string? modelVersion = default!,
        int? promptTokens = default!,
        int? completionTokens = default!,
        decimal? totalCostUsd = default!,
        int? latencyMs = default!,
        string? errorCode = default!,
        string? errorMessage = default!,
        Guid? requestedByUserId = default!,
        DateTime? startedAt = default!,
        DateTime? completedAt = default!,
        int maxRetries = 3)  
    {
        JobType = jobType;
        QueuedAt = queuedAt;
        RetryCount = retryCount;
        MissionImageId = missionImageId;
        DefectId = defectId;
        ReportId = reportId;
        Status = status;
        Priority = priority;
        InputPayload = inputPayload;
        Result = result;
        Confidence = confidence;
        ModelName = modelName;
        ModelVersion = modelVersion;
        PromptTokens = promptTokens;
        CompletionTokens = completionTokens;
        TotalCostUsd = totalCostUsd;
        LatencyMs = latencyMs;
        ErrorCode = errorCode;
        ErrorMessage = errorMessage;
        RequestedByUserId = requestedByUserId;
        StartedAt = startedAt;
        CompletedAt = completedAt;
        MaxRetries = maxRetries;
    }

    public Guid? MissionImageId { get; private set; }
    public Guid? DefectId { get; private set; }
    public Guid? ReportId { get; private set; }
    public AiJobType JobType { get; private set; } = default!;
    public AiJobStatus Status { get; private set; } = AiJobStatus.Queued;
    public int Priority { get; private set; } = 5;
    public JsonDocument? InputPayload { get; private set; }
    public JsonDocument? Result { get; private set; }
    public decimal? Confidence { get; private set; }
    public string? ModelName { get; private set; }
    public string? ModelVersion { get; private set; }
    public int? PromptTokens { get; private set; }
    public int? CompletionTokens { get; private set; }
    public decimal? TotalCostUsd { get; private set; }
    public int? LatencyMs { get; private set; }
    public string? ErrorCode { get; private set; }
    public string? ErrorMessage { get; private set; }
    public Guid? RequestedByUserId { get; private set; }
    public DateTime QueuedAt { get; private set; }
    public DateTime? StartedAt { get; private set; }
    public DateTime? CompletedAt { get; private set; }
    public int RetryCount { get; private set; }
    public int MaxRetries { get; private set; } = 3;

    public AiAnalysisJob UpdateMissionImageId(Guid? newMissionImageId)
    {
        MissionImageId = newMissionImageId;
        return this;
    }

    public AiAnalysisJob UpdateDefectId(Guid? newDefectId)
    {
        DefectId = newDefectId;
        return this;
    }

    public AiAnalysisJob UpdateReportId(Guid? newReportId)
    {
        ReportId = newReportId;
        return this;
    }

    public AiAnalysisJob UpdateJobType(AiJobType newJobType)
    {
        JobType = newJobType;
        return this;
    }

    public AiAnalysisJob UpdateStatus(AiJobStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public AiAnalysisJob UpdatePriority(int newPriority)
    {
        Priority = newPriority;
        return this;
    }

    public AiAnalysisJob UpdateInputPayload(JsonDocument? newInputPayload)
    {
        InputPayload = newInputPayload;
        return this;
    }

    public AiAnalysisJob UpdateResult(JsonDocument? newResult)
    {
        Result = newResult;
        return this;
    }

    public AiAnalysisJob UpdateConfidence(decimal? newConfidence)
    {
        Confidence = newConfidence;
        return this;
    }

    public AiAnalysisJob UpdateModelName(string? newModelName)
    {
        ModelName = newModelName;
        return this;
    }

    public AiAnalysisJob UpdateModelVersion(string? newModelVersion)
    {
        ModelVersion = newModelVersion;
        return this;
    }

    public AiAnalysisJob UpdatePromptTokens(int? newPromptTokens)
    {
        PromptTokens = newPromptTokens;
        return this;
    }

    public AiAnalysisJob UpdateCompletionTokens(int? newCompletionTokens)
    {
        CompletionTokens = newCompletionTokens;
        return this;
    }

    public AiAnalysisJob UpdateTotalCostUsd(decimal? newTotalCostUsd)
    {
        TotalCostUsd = newTotalCostUsd;
        return this;
    }

    public AiAnalysisJob UpdateLatencyMs(int? newLatencyMs)
    {
        LatencyMs = newLatencyMs;
        return this;
    }

    public AiAnalysisJob UpdateErrorCode(string? newErrorCode)
    {
        ErrorCode = newErrorCode;
        return this;
    }

    public AiAnalysisJob UpdateErrorMessage(string? newErrorMessage)
    {
        ErrorMessage = newErrorMessage;
        return this;
    }

    public AiAnalysisJob UpdateRequestedByUserId(Guid? newRequestedByUserId)
    {
        RequestedByUserId = newRequestedByUserId;
        return this;
    }

    public AiAnalysisJob UpdateQueuedAt(DateTime newQueuedAt)
    {
        QueuedAt = newQueuedAt;
        return this;
    }

    public AiAnalysisJob UpdateStartedAt(DateTime? newStartedAt)
    {
        StartedAt = newStartedAt;
        return this;
    }

    public AiAnalysisJob UpdateCompletedAt(DateTime? newCompletedAt)
    {
        CompletedAt = newCompletedAt;
        return this;
    }

    public AiAnalysisJob UpdateRetryCount(int newRetryCount)
    {
        RetryCount = newRetryCount;
        return this;
    }

    public AiAnalysisJob UpdateMaxRetries(int newMaxRetries)
    {
        MaxRetries = newMaxRetries;
        return this;
    }

}
