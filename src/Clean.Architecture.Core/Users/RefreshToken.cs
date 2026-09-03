using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Users;

public class RefreshToken : EntityBase<RefreshToken, RefreshTokenId>, IAggregateRoot
{
    private RefreshToken() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public RefreshToken(
        Guid userId = default,
        string tokenHash = default!,
        string jwtId = default!,
        DateTime expiresAt = default,
        User? user = default!,
        DateTime? revokedAt = default!,
        string? revokedReason = default!,
        Guid? replacedByTokenId = default!,
        string? userAgent = default!,
        string? ipAddress = default!)  
    {
        UserId = Guard.Against.Default(userId, nameof(userId));
        TokenHash = Guard.Against.NullOrWhiteSpace(tokenHash, nameof(tokenHash));
        JwtId = Guard.Against.NullOrWhiteSpace(jwtId, nameof(jwtId));
        ExpiresAt = expiresAt;
        User = user;
        RevokedAt = revokedAt;
        RevokedReason = revokedReason;
        ReplacedByTokenId = replacedByTokenId;
        UserAgent = userAgent;
        IpAddress = ipAddress;
    }

    public Guid UserId { get; private set; }
    public User? User { get; private set; }
    public string TokenHash { get; private set; } = string.Empty;
    public string JwtId { get; private set; } = string.Empty;
    public DateTime ExpiresAt { get; private set; }
    public DateTime? RevokedAt { get; private set; }
    public string? RevokedReason { get; private set; }
    public Guid? ReplacedByTokenId { get; private set; }
    public string? UserAgent { get; private set; }
    public string? IpAddress { get; private set; }

    public RefreshToken UpdateUserId(Guid newUserId)
    {
        UserId = newUserId;
        return this;
    }

    public RefreshToken UpdateUser(User? newUser)
    {
        User = newUser;
        return this;
    }

    public RefreshToken UpdateTokenHash(string newTokenHash)
    {
        TokenHash = Guard.Against.NullOrWhiteSpace(newTokenHash, nameof(newTokenHash));
        return this;
    }

    public RefreshToken UpdateJwtId(string newJwtId)
    {
        JwtId = Guard.Against.NullOrWhiteSpace(newJwtId, nameof(newJwtId));
        return this;
    }

    public RefreshToken UpdateExpiresAt(DateTime newExpiresAt)
    {
        ExpiresAt = newExpiresAt;
        return this;
    }

    public RefreshToken UpdateRevokedAt(DateTime? newRevokedAt)
    {
        RevokedAt = newRevokedAt;
        return this;
    }

    public RefreshToken UpdateRevokedReason(string? newRevokedReason)
    {
        RevokedReason = newRevokedReason;
        return this;
    }

    public RefreshToken UpdateReplacedByTokenId(Guid? newReplacedByTokenId)
    {
        ReplacedByTokenId = newReplacedByTokenId;
        return this;
    }

    public RefreshToken UpdateUserAgent(string? newUserAgent)
    {
        UserAgent = newUserAgent;
        return this;
    }

    public RefreshToken UpdateIpAddress(string? newIpAddress)
    {
        IpAddress = newIpAddress;
        return this;
    }

}
