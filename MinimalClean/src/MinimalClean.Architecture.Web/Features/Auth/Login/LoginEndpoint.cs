using FastEndpoints;
using FluentValidation;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using MinimalClean.Architecture.Web.Domain.Interfaces;
using MinimalClean.Architecture.Web.Domain.Users;
using MinimalClean.Architecture.Web.Infrastructure.Data;

namespace MinimalClean.Architecture.Web.Features.Auth.Login;

public sealed record LoginRequest
{
    public string Email { get; init; } = string.Empty;
    public string Password { get; init; } = string.Empty;
}

public sealed class LoginValidator : Validator<LoginRequest>
{
    public LoginValidator()
    {
        RuleFor(x => x.Email).NotEmpty().EmailAddress().MaximumLength(320);
        RuleFor(x => x.Password).NotEmpty().MaximumLength(128);
    }
}

public sealed class LoginEndpoint(
    AppDbContext db,
    ITokenService tokens,
    IPasswordHasher hasher) 
    : Endpoint<LoginRequest, Results<Ok<AuthResponse>, UnauthorizedHttpResult>>
{
    private const int MaxFailedLogins = 5;
    private static readonly TimeSpan LockoutDuration = TimeSpan.FromMinutes(15);
    private const string DummyHash = "AQAAAAIAAYagAAAAEJ+Ri1Z0hUqH3GyT0YqPZ+D3W3Xm6M8Kq7kQeJ5VX2Fh9Lp3Rn0TzW1bC4aD2vUqA==";

    public override void Configure()
    {
        Post("/auth/login");
        AllowAnonymous();
        Tags("Auth");
        Summary(s =>
        {
            s.Summary = "User login with email and password";
            s.Description = "Authenticates user credentials, enforces account lockout policies, and returns JWT access + refresh tokens.";
        });
    }

    public override async Task<Results<Ok<AuthResponse>, UnauthorizedHttpResult>> ExecuteAsync(LoginRequest req, CancellationToken ct)
    {
        var normalizedEmail = req.Email.Trim().ToUpperInvariant();
        var user = await db.Users.FirstOrDefaultAsync(u => u.NormalizedEmail == normalizedEmail, ct);

        if (user is null)
        {
            // Burn comparable time to prevent user enumeration
            hasher.VerifyPassword(DummyHash, req.Password);
            return TypedResults.Unauthorized();
        }

        if (user.LockoutEndAt is { } lockoutEnd && lockoutEnd > DateTime.UtcNow)
        {
            return TypedResults.Unauthorized();
        }

        if (!user.IsActive || user.IsDeleted)
        {
            return TypedResults.Unauthorized();
        }

        var result = hasher.VerifyPassword(user.PasswordHash, req.Password);
        if (result == PasswordVerification.Failed)
        {
            user.FailedLoginCount += 1;
            if (user.FailedLoginCount >= MaxFailedLogins)
            {
                user.LockoutEndAt = DateTime.UtcNow.Add(LockoutDuration);
                user.FailedLoginCount = 0;
            }
            await db.SaveChangesAsync(ct);
            return TypedResults.Unauthorized();
        }

        if (result == PasswordVerification.SuccessRehashNeeded)
        {
            user.PasswordHash = hasher.HashPassword(req.Password);
        }

        user.FailedLoginCount = 0;
        user.LockoutEndAt = null;
        user.LastLoginAt = DateTime.UtcNow;
        user.LastLoginIp = HttpContext.Connection.RemoteIpAddress?.ToString();

        var (accessToken, jwtId, _) = tokens.CreateAccessToken(user.Id.Value, user.Email, user.Role.Name);
        var (rawRefresh, refreshHash) = tokens.CreateRefreshToken();

        db.RefreshTokens.Add(new RefreshToken(
            userId: user.Id.Value,
            tokenHash: refreshHash,
            jwtId: jwtId,
            expiresAt: DateTime.UtcNow.AddDays(7),
            ipAddress: HttpContext.Connection.RemoteIpAddress?.ToString(),
            userAgent: HttpContext.Request.Headers.UserAgent.ToString()));

        await db.SaveChangesAsync(ct);

        var response = new AuthResponse(
            user.Id.Value,
            user.Email,
            user.FullName,
            user.Role.Name,
            accessToken,
            rawRefresh);

        return TypedResults.Ok(response);
    }
}
