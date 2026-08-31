using SmartDroneInspection.UnitTests.Common;
using SmartDroneInspection.Application.Users.Commands;
using SmartDroneInspection.Application.Users.Dtos;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.UnitTests.Users.Commands;

public class RefreshTokenCommandTests
{
    private readonly FakeTokenService _tokens = new();

    private static async Task<TestDbContext> ContextWithUserAsync(User? user = null)
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(user ?? new User
        {
            Id = Guid.NewGuid(),
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            FullName = "Test User",
            Role = UserRole.Inspector,
        });
        await ctx.SaveChangesAsync();
        return ctx;
    }

    [Fact]
    public async Task Handle_UnknownToken_ThrowsUnauthorized()
    {
        var ctx = await ContextWithUserAsync();
        var handler = new RefreshTokenCommandHandler(ctx, _tokens);

        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new RefreshTokenCommand("no-such-token", null, null), CancellationToken.None));
    }

    [Fact]
    public async Task Handle_ExpiredToken_ThrowsUnauthorized()
    {
        var ctx = await ContextWithUserAsync();
        var user = ctx.Users.Single();
        var raw = "raw-token";
        ctx.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = _tokens.HashOf(raw),
            JwtId = Guid.NewGuid().ToString(),
            ExpiresAt = DateTime.UtcNow.AddMinutes(-1),
        });
        await ctx.SaveChangesAsync();

        var handler = new RefreshTokenCommandHandler(ctx, _tokens);
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new RefreshTokenCommand(raw, null, null), CancellationToken.None));
    }

    [Fact]
    public async Task Handle_ValidToken_RotatesAndIssuesNewPair()
    {
        var ctx = await ContextWithUserAsync();
        var user = ctx.Users.Single();
        var raw = "raw-token";
        ctx.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = _tokens.HashOf(raw),
            JwtId = Guid.NewGuid().ToString(),
            ExpiresAt = DateTime.UtcNow.AddDays(7),
        });
        await ctx.SaveChangesAsync();

        var handler = new RefreshTokenCommandHandler(ctx, _tokens);
        var response = await handler.Handle(
            new RefreshTokenCommand(raw, "10.0.0.2", "agent"), CancellationToken.None);

        Assert.NotNull(response.AccessToken);

        var old = ctx.RefreshTokens.Single(t => t.TokenHash == _tokens.HashOf(raw));
        Assert.NotNull(old.RevokedAt);
        Assert.Equal("replaced", old.RevokedReason);

        var stored = ctx.RefreshTokens.Single(t => t.TokenHash == _tokens.HashOf(response.RefreshToken));
        Assert.Equal(stored.Id, old.ReplacedByTokenId);
    }

    [Fact]
    public async Task Handle_ReusedRevokedToken_RevokesEntireChain()
    {
        var ctx = await ContextWithUserAsync();
        var user = ctx.Users.Single();
        var reused = "reused-token";
        var other = "other-token";
        ctx.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = _tokens.HashOf(reused),
            JwtId = Guid.NewGuid().ToString(),
            ExpiresAt = DateTime.UtcNow.AddDays(7),
            RevokedAt = DateTime.UtcNow.AddMinutes(-10),
            RevokedReason = "replaced",
        });
        ctx.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = _tokens.HashOf(other),
            JwtId = Guid.NewGuid().ToString(),
            ExpiresAt = DateTime.UtcNow.AddDays(7),
        });
        await ctx.SaveChangesAsync();

        var handler = new RefreshTokenCommandHandler(ctx, _tokens);
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new RefreshTokenCommand(reused, null, null), CancellationToken.None));

        var otherToken = ctx.RefreshTokens.Single(t => t.TokenHash == _tokens.HashOf(other));
        Assert.NotNull(otherToken.RevokedAt);
        Assert.Equal("reuse-detected", otherToken.RevokedReason);
    }

    [Fact]
    public async Task Handle_DisabledUser_ThrowsUnauthorized()
    {
        var ctx = await ContextWithUserAsync(new User
        {
            Id = Guid.NewGuid(),
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            IsActive = false,
        });
        var user = ctx.Users.Single();
        var raw = "raw-token";
        ctx.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = _tokens.HashOf(raw),
            JwtId = Guid.NewGuid().ToString(),
            ExpiresAt = DateTime.UtcNow.AddDays(7),
        });
        await ctx.SaveChangesAsync();

        var handler = new RefreshTokenCommandHandler(ctx, _tokens);
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new RefreshTokenCommand(raw, null, null), CancellationToken.None));
    }
}
