namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>Current authenticated user context (JWT claims).</summary>
public interface ICurrentUserService
{
    Guid? UserId { get; }
    string? UserName { get; }
    IReadOnlyList<string> Roles { get; }
    bool IsInRole(string role);
}
