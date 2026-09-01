using MediatR;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Application.Users.Commands;
using SmartDroneInspection.Application.Users.Dtos;

namespace SmartDroneInspection.Api.Controllers;

/// <summary>
/// Authentication endpoints. Login + refresh are throttled by the "auth" rate-limit
/// policy (10 req/min per IP) for brute-force protection.
/// </summary>
[ApiController]
[Route("api/v{version:apiVersion}/auth")]
[EnableRateLimiting("auth")]
public class AuthController(IMediator mediator, ICurrentUserService currentUser) : ControllerBase
{
    [HttpPost("login")]
    [ProducesResponseType(typeof(AuthResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<AuthResponse>> LoginAsync(
        [FromBody] LoginRequest request, CancellationToken ct)
    {
        var result = await mediator.Send(
            new LoginCommand(request.Email, request.Password, currentUser.ClientIp, currentUser.UserAgent), ct);
        return Ok(result);
    }

    [HttpPost("refresh")]
    [ProducesResponseType(typeof(AuthResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<AuthResponse>> RefreshAsync(
        [FromBody] RefreshRequest request, CancellationToken ct)
    {
        var result = await mediator.Send(
            new RefreshTokenCommand(request.RefreshToken, currentUser.ClientIp, currentUser.UserAgent), ct);
        return Ok(result);
    }
}
