namespace Clean.Architecture.Web.Assets;

public class GetAssetByIdRequest
{
  public const string Route = "/Assets/{AssetId:guid}";
  public static string BuildRoute(Guid assetId) => Route.Replace("{AssetId:guid}", assetId.ToString());

  public Guid AssetId { get; set; }
}
