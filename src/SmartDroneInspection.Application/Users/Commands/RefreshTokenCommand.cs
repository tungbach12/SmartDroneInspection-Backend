using FluentValidation;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Application.Users.Dtos;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Application.Users.Commands;

public record RefreshTokenCommand(string RefreshToken, string? IpAddress, string? UserAgent)
    : IRequest<AuthResponse>;

public class RefreshTokenCommandValidator : AbstractValidator<RefreshTokenCommand>
{
    public RefreshTokenCommandValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty().MaximumLength(256);
    }
}

/// <summary>
/// Rotates a refresh token: verifies hash + expiry, revokes the old row, issues
/// a new pair. Reuse of an already-revoked token revokes the user's entire
/// token chain (stolen-token detection per refresh-token-rotation best practice).
/// </summary>
public class RefreshTokenCommandHandler(
    IApplicationDbContext db,
    ITokenService tokens)
    : IRequestHandler<RefreshTokenCommand, AuthResponse>
{
    public async Task<AuthResponse> Handle(RefreshTokenCommand request, CancellationToken ct)
    {
        string hash;
        try
        {
            hash = tokens.HashToken(request.RefreshToken);
        }
        catch (FormatException)
        {
            throw new UnauthorizedAccessException("Invalid refresh token.");
        }

        var stored = await db.RefreshTokens
            .Include(t => t.User)
            .FirstOrDefaultAsync(t => t.TokenHash == hash, ct);

        if (stored is null || stored.ExpiresAt <= DateTime.UtcNow)
        {
            throw new UnauthorizedAccessException("Invalid refresh token.");
        }

        if (stored.RevokedAt is not null)
        {
            // Token reuse detected — revoke every active token of this user.
            var activeTokens = await db.RefreshTokens
                .Where(t => t.UserId == stored.UserId && t.RevokedAt == null)
                .ToListAsync(ct);
            foreach (var token in activeTokens)
            {
                token.RevokedAt = DateTime.UtcNow;
                token.RevokedReason = "reuse-detected";
            }
            await db.SaveChangesAsync(ct);
            throw new UnauthorizedAccessException("Invalid refresh token.");
        }
        if (stored.User is null || !stored.User.IsActive || stored.User.IsDeleted)
        {
            throw new UnauthorizedAccessException("Account is disabled.");
        }

        var (accessToken, jwtId, expiresAt) = tokens.CreateAccessToken(
            stored.User.Id, stored.User.Email, stored.User.Role.ToString());
        var (rawRefresh, refreshHash) = tokens.CreateRefreshToken();

        var replacement = new RefreshToken
        {
            UserId = stored.UserId,
            TokenHash = refreshHash,
            JwtId = jwtId,
            ExpiresAt = DateTime.UtcNow.AddDays(7),
            IpAddress = request.IpAddress,
            UserAgent = request.UserAgent,
        };
        db.RefreshTokens.Add(replacement);

        stored.RevokedAt = DateTime.UtcNow;
        stored.RevokedReason = "replaced";
        stored.ReplacedByTokenId = replacement.Id;

        await db.SaveChangesAsync(ct);

        return new AuthResponse(
            stored.User.Id, stored.User.Email, stored.User.FullName, stored.User.Role.ToString(),
            accessToken, rawRefresh);
    }
}
