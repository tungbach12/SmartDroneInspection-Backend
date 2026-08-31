using System.IdentityModel.Tokens.Jwt;
using SmartDroneInspection.Application.Common.Interfaces;
using System.Security.Claims;
using System.Security.Cryptography;
using Microsoft.Extensions.Configuration;
using Microsoft.IdentityModel.Tokens;

namespace SmartDroneInspection.Infrastructure.Auth;

/// <summary>
/// Issues JWT access tokens and opaque refresh tokens.
/// Access tokens carry sub/email/role/jti; refresh tokens are random 64-byte
/// strings stored only as SHA-256 hashes (never persisted in plaintext).
/// </summary>
public sealed class JwtTokenService(IConfiguration configuration) : ITokenService
{
    public (string Token, string JwtId, DateTime ExpiresAtUtc) CreateAccessToken(
        Guid userId, string email, string role)
    {
        var jwtId = Guid.NewGuid().ToString();
        var expiresAt = DateTime.UtcNow.AddMinutes(AccessTokenMinutes);

        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Sub, userId.ToString()),
            new(JwtRegisteredClaimNames.Email, email),
            new(JwtRegisteredClaimNames.Jti, jwtId),
            new(ClaimTypes.NameIdentifier, userId.ToString()),
            new(ClaimTypes.Name, email),
            new(ClaimTypes.Role, role),
        };

        var token = new JwtSecurityToken(
            issuer: Issuer,
            audience: Audience,
            claims: claims,
            notBefore: DateTime.UtcNow,
            expires: expiresAt,
            signingCredentials: new SigningCredentials(
                new SymmetricSecurityKey(Convert.FromBase64String(SigningKey)),
                SecurityAlgorithms.HmacSha256));

        return (new JwtSecurityTokenHandler().WriteToken(token), jwtId, expiresAt);
    }

    /// <summary>Random refresh token; callers persist <see cref="HashToken"/>, not the raw value.</summary>
    public (string RawToken, string Hash) CreateRefreshToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(64);
        var raw = Convert.ToBase64String(bytes);
        return (raw, HashToken(raw));
    }

    public string HashToken(string rawToken) =>
        Convert.ToHexString(SHA256.HashData(Convert.FromBase64String(rawToken)));

    private string Issuer => configuration["Jwt:Issuer"] ?? throw new InvalidOperationException("Jwt:Issuer is not configured");
    private string Audience => configuration["Jwt:Audience"] ?? throw new InvalidOperationException("Jwt:Audience is not configured");
    private string SigningKey => configuration["Jwt:Key"] ?? throw new InvalidOperationException("Jwt:Key is not configured");
    private int AccessTokenMinutes => configuration.GetValue("Jwt:AccessTokenMinutes", 15);
}
