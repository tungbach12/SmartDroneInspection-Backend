namespace Clean.Architecture.Core.Planning;

public class PlanAsset
{
    public Guid PlanId { get; set; }
    public Guid AssetId { get; set; }
    public int SortOrder { get; set; }
}
