namespace Clean.Architecture.Web.Assets;

public record AssetRecord(
  Guid Id,
  string Code,
  string Name,
  string Status,
  string Address);
