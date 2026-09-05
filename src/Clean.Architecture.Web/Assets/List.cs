using Clean.Architecture.UseCases;
using Clean.Architecture.UseCases.Assets;
using Clean.Architecture.UseCases.Assets.List;
using FluentValidation;

namespace Clean.Architecture.Web.Assets;

public class List(IMediator mediator) : Endpoint<ListAssetsRequest, AssetListResponse, ListAssetsMapper>
{
  private readonly IMediator _mediator = mediator;

  public override void Configure()
  {
    Get("/Assets");
    AllowAnonymous();

    Summary(s =>
    {
      s.Summary = "List assets with pagination";
      s.Description = "Retrieves a paginated list of all assets. Supports search by name/code and configurable page size.";
      s.ExampleRequest = new ListAssetsRequest { Page = 1, PageSize = 10 };
      s.ResponseExamples[200] = new AssetListResponse(
        new List<AssetRecord>
        {
          new(Guid.NewGuid(), "AST-001", "Power Transformer A1", "Active", "Hanoi, Vietnam"),
          new(Guid.NewGuid(), "AST-002", "Wind Turbine B2", "Active", "Da Nang, Vietnam")
        },
        1, 10, 2, 1);

      s.Params["page"] = "1-based page index (default 1)";
      s.Params["page_size"] = $"Page size 1–{Constants.MAX_PAGE_SIZE} (default {Constants.DEFAULT_PAGE_SIZE})";

      s.Responses[200] = "Paginated list of assets returned successfully";
      s.Responses[400] = "Invalid pagination parameters";
    });

    Tags("Assets");

    Description(builder => builder
      .Accepts<ListAssetsRequest>()
      .Produces<AssetListResponse>(200, "application/json")
      .ProducesProblem(400));
  }

  public override async Task HandleAsync(ListAssetsRequest request, CancellationToken cancellationToken)
  {
    var result = await _mediator.Send(new ListAssetsQuery(
      Search: null,
      SortBy: null,
      SortDescending: false,
      Page: request.Page,
      PageSize: request.PageSize));

    if (!result.IsSuccess)
    {
      await Send.ErrorsAsync(statusCode: 400, cancellationToken);
      return;
    }

    var paged = result.Value;
    var response = Map.FromEntity(paged);
    await Send.OkAsync(response, cancellationToken);
  }
}

public class ListAssetsRequest
{
  // Bind to ?page=
  [BindFrom("page")]
  public int Page { get; init; } = 1;

  // Bind to ?page_size=
  [BindFrom("page_size")]
  public int PageSize { get; init; } = Constants.DEFAULT_PAGE_SIZE;
}

public record AssetListResponse : UseCases.PagedResult<AssetRecord>
{
  public AssetListResponse(IReadOnlyList<AssetRecord> Items, int Page, int PerPage, int TotalCount, int TotalPages)
    : base(Items, Page, PerPage, TotalCount, TotalPages)
  {
  }
}

public sealed class ListAssetsValidator : Validator<ListAssetsRequest>
{
  public ListAssetsValidator()
  {
    RuleFor(x => x.Page)
      .GreaterThanOrEqualTo(1)
      .WithMessage("page must be >= 1");

    RuleFor(x => x.PageSize)
      .InclusiveBetween(1, Constants.MAX_PAGE_SIZE)
      .WithMessage($"page_size must be between 1 and {Constants.MAX_PAGE_SIZE}");
  }
}

public sealed class ListAssetsMapper
  : Mapper<ListAssetsRequest, AssetListResponse, UseCases.PagedResult<AssetDto>>
{
  public override AssetListResponse FromEntity(UseCases.PagedResult<AssetDto> e)
  {
    var items = e.Items
      .Select(a => new AssetRecord(a.Id.Value, a.Code, a.Name, a.Status, a.Address ?? string.Empty))
      .ToList();

    return new AssetListResponse(items, e.Page, e.PerPage, e.TotalCount, e.TotalPages);
  }
}
