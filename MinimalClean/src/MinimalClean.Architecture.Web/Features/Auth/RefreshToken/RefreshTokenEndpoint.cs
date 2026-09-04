using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using MinimalClean.Architecture.Web.Domain.Interfaces;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data;

namespace MinimalClean.Architecture.Web.Features.Auth.RefreshToken;

public sealed record RefreshTokenRequest
{
    public string RefreshToken { get; init; } = string.Empty;
}

public sealed class RefreshTokenValidator : Validator<RefreshTokenRequest>
{
    public RefreshTokenValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty();
    }
}

public sealed class RefreshTokenEndpoint(
    AppDbContext db,
    ITokenService tokens) 
    : Endpoint<RefreshTokenRequest, Results<Ok<AuthResponse>, UnauthorizedHttpResult>>
{
    public override void Configure()
    {
        Post("/auth/refresh");
        AllowAnonymous();
        Tags("Auth");
        Summary(s =>
        {
            s.Summary = "Refresh JWT access token";
            s.Description = "Exchanges a valid refresh token for a newly issued access token and rotated refresh token.";
        });
    }

    public override async Task<Results<Ok<AuthResponse>, UnauthorizedHttpResult>> ExecuteAsync(RefreshTokenRequest req, CancellationToken ct)
    {
        var tokenHash = tokens.HashToken(req.RefreshToken);
        var storedToken = await db.RefreshTokens
            .Include(r => r.User)
            .FirstOrDefaultAsync(r => r.TokenHash == tokenHash, ct);

        if (storedToken is null || storedToken.IsRevoked || storedToken.ExpiresAt <= DateTime.UtcNow)
        {
            return TypedResults.Unauthorized();
        }

        var user = storedToken.User;
        if (user is null || !user.IsActive || user.IsDeleted)
        {
            return TypedResults.Unauthorized();
        }

        // Single-use token rotation: revoke used token
        storedToken.IsRevoked = true;
        storedToken.RevokedAt = DateTime.UtcNow;
        storedToken.ReplacedByToken = "ROTATED";

        var (newAccessToken, newJwtId, _) = tokens.CreateAccessToken(user.Id.Value, user.Email, user.Role.Name);
        var (newRawRefresh, newRefreshHash) = tokens.CreateRefreshToken();

        db.RefreshTokens.Add(new Domain.Users.RefreshToken(
            userId: user.Id.Value,
            tokenHash: newRefreshHash,
            jwtId: newJwtId,
            expiresAt: DateTime.UtcNow.AddDays(7),
            ipAddress: HttpContext.Connection.RemoteIpAddress?.ToString(),
            userAgent: HttpContext.Request.Headers.UserAgent.ToString()));

        await db.SaveChangesAsync(ct);

        var response = new AuthResponse(
            user.Id.Value,
            user.Email,
            user.FullName,
            user.Role.Name,
            newAccessToken,
            newRawRefresh);

        return TypedResults.Ok(response);
    }
}
