using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using SmartDroneInspection.Application.Common.Interfaces;

namespace SmartDroneInspection.Infrastructure.Auth;

/// <summary>Reads the authenticated user from JWT claims (set by UseAuthentication).</summary>
public sealed class CurrentUserService(IHttpContextAccessor accessor) : ICurrentUserService
{
    private HttpContext? Context => accessor.HttpContext;
    private ClaimsPrincipal? Principal => Context?.User;

    public Guid? UserId =>
        Guid.TryParse(Principal?.FindFirstValue(ClaimTypes.NameIdentifier), out var id) ? id : null;

    public string? UserName => Principal?.FindFirstValue(ClaimTypes.Name);

    public IReadOnlyList<string> Roles => Principal
        ?.FindAll(ClaimTypes.Role)
        .Select(c => c.Value)
        .ToList()
        ?? [];

    public bool IsInRole(string role) => Principal?.IsInRole(role) ?? false;

    public string? ClientIp => Context?.Connection.RemoteIpAddress?.ToString();

    public string? UserAgent => Context?.Request.Headers.UserAgent.ToString();
}
