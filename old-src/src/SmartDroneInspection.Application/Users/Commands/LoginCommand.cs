using FluentValidation;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Application.Users.Dtos;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Application.Users.Commands;

public record LoginCommand(string Email, string Password, string? IpAddress, string? UserAgent)
    : IRequest<AuthResponse>;

public class LoginCommandValidator : AbstractValidator<LoginCommand>
{
    public LoginCommandValidator()
    {
        RuleFor(x => x.Email).NotEmpty().EmailAddress().MaximumLength(320);
        RuleFor(x => x.Password).NotEmpty().MaximumLength(128);
    }
}

/// <summary>
/// Verifies credentials, enforces lockout/IsActive, and issues a refresh token.
/// Generic error message so attackers can't enumerate which emails exist.
/// </summary>
public class LoginCommandHandler(
    IApplicationDbContext db,
    ITokenService tokens,
    IPasswordHasher hasher)
    : IRequestHandler<LoginCommand, AuthResponse>
{
    private const int MaxFailedLogins = 5;
    private static readonly TimeSpan LockoutDuration = TimeSpan.FromMinutes(15);
    private const string DummyHash = "AQAAAAIAAYagAAAAEJ+Ri1Z0hUqH3GyT0YqPZ+D3W3Xm6M8Kq7kQeJ5VX2Fh9Lp3Rn0TzW1bC4aD2vUqA==";

    public async Task<AuthResponse> Handle(LoginCommand request, CancellationToken ct)
    {
        var normalizedEmail = request.Email.Trim().ToUpperInvariant();
        var user = await db.Users.FirstOrDefaultAsync(u => u.NormalizedEmail == normalizedEmail, ct);

        if (user is null)
        {
            // Burn comparable time so response latency doesn't reveal user existence.
            hasher.VerifyPassword(DummyHash, request.Password);
            throw new UnauthorizedAccessException("Invalid email or password.");
        }

        if (user.LockoutEndAt is { } lockoutEnd && lockoutEnd > DateTime.UtcNow)
        {
            throw new UnauthorizedAccessException("Account is temporarily locked. Try again later.");
        }

        if (!user.IsActive || user.IsDeleted)
        {
            throw new UnauthorizedAccessException("Account is disabled.");
        }

        var result = hasher.VerifyPassword(user.PasswordHash, request.Password);
        if (result == PasswordVerification.Failed)
        {
            user.FailedLoginCount += 1;
            if (user.FailedLoginCount >= MaxFailedLogins)
            {
                user.LockoutEndAt = DateTime.UtcNow.Add(LockoutDuration);
                user.FailedLoginCount = 0;
            }
            await db.SaveChangesAsync(ct);
            throw new UnauthorizedAccessException("Invalid email or password.");
        }

        if (result == PasswordVerification.SuccessRehashNeeded)
        {
            user.PasswordHash = hasher.HashPassword(request.Password);
        }

        user.FailedLoginCount = 0;
        user.LockoutEndAt = null;
        user.LastLoginAt = DateTime.UtcNow;
        user.LastLoginIp = request.IpAddress;

        var (accessToken, jwtId, _) = tokens.CreateAccessToken(user.Id, user.Email, user.Role.ToString());
        var (rawRefresh, refreshHash) = tokens.CreateRefreshToken();

        db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = refreshHash,
            JwtId = jwtId,
            ExpiresAt = DateTime.UtcNow.AddDays(7),
            IpAddress = request.IpAddress,
            UserAgent = request.UserAgent,
        });

        await db.SaveChangesAsync(ct);

        return new AuthResponse(
            user.Id, user.Email, user.FullName, user.Role.ToString(),
            accessToken, rawRefresh);
    }
}
