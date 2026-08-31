using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SmartDroneInspection.Application.Assets.Commands;
using SmartDroneInspection.Application.Assets.Dtos;
using SmartDroneInspection.Application.Assets.Queries;
using SmartDroneInspection.Application.Common.Models;
using SmartDroneInspection.Domain.Common;

namespace SmartDroneInspection.Api.Controllers;

[ApiController]
[Route("api/v{version:apiVersion}/assets")]
[Authorize]
public class AssetsController(IMediator mediator) : ControllerBase
{
    /// <summary>Paged asset list with search + sort.</summary>
    [HttpGet]
    public async Task<ActionResult<PagedResult<AssetDto>>> GetAssets([FromQuery] AssetListQuery filters, CancellationToken ct)
    {
        var result = await mediator.Send(new GetAssetsQuery(filters), ct);
        return Ok(result);
    }

    /// <summary>Get a single asset by id.</summary>
    [HttpGet("{id:guid}")]
    public async Task<ActionResult<AssetDto>> GetById(Guid id, CancellationToken ct)
    {
        var result = await mediator.Send(new GetAssetByIdQuery(id), ct);
        return Ok(result);
    }

    /// <summary>Create a new asset.</summary>
    [HttpPost]
    [Authorize(Roles = $"{Roles.Administrator},{Roles.InspectionManager}")]
    public async Task<ActionResult<Guid>> Create(CreateAssetRequest request, CancellationToken ct)
    {
        var command = new CreateAssetCommand(
            request.Name, request.Code, request.Description, request.CategoryId,
            request.OrganizationId, request.Address, request.Region,
            request.Latitude, request.Longitude);

        var id = await mediator.Send(command, ct);
        return CreatedAtAction(nameof(GetById), new { id, version = "1" }, id);
    }
}
