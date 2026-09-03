namespace MinimalClean.Architecture.Web.Domain.Planning;

public class PlanAsset
{
    public Guid PlanId { get; set; }
    public Guid AssetId { get; set; }
    public int SortOrder { get; set; }
}
