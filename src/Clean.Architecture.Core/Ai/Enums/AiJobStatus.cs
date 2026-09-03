using Ardalis.SmartEnum;

namespace Clean.Architecture.Core.Ai.Enums;

public sealed class AiJobStatus : SmartEnum<AiJobStatus>
{
    public static readonly AiJobStatus Queued = new(nameof(Queued), 0);
    public static readonly AiJobStatus Processing = new(nameof(Processing), 1);
    public static readonly AiJobStatus Completed = new(nameof(Completed), 2);
    public static readonly AiJobStatus Failed = new(nameof(Failed), 3);
    public static readonly AiJobStatus Timeout = new(nameof(Timeout), 4);
    public static readonly AiJobStatus Cancelled = new(nameof(Cancelled), 5);

    private AiJobStatus(string name, int value) : base(name, value) { }
}
