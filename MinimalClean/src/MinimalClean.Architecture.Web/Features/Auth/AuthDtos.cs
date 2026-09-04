namespace MinimalClean.Architecture.Web.Features.Auth;

public sealed record AuthResponse(
    Guid UserId,
    string Email,
    string FullName,
    string Role,
    string AccessToken,
    string RefreshToken);
