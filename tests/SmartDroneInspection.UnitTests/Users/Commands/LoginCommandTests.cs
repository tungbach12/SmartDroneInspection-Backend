using SmartDroneInspection.UnitTests.Common;
using FluentValidation.TestHelper;
using SmartDroneInspection.Application.Users.Commands;
using SmartDroneInspection.Application.Users.Dtos;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.UnitTests.Users.Commands;

public class LoginCommandValidatorTests
{
    private readonly LoginCommandValidator _validator = new();

    [Theory]
    [InlineData("")]
    [InlineData("not-an-email")]
    [InlineData("  ")]
    public void Validate_InvalidEmail_Fails(string email)
    {
        var result = _validator.TestValidate(new LoginCommand(email, "Password123!", null, null));
        result.ShouldHaveValidationErrorFor(x => x.Email);
    }

    [Fact]
    public void Validate_MissingPassword_Fails()
    {
        var result = _validator.TestValidate(new LoginCommand("admin@test.com", "", null, null));
        result.ShouldHaveValidationErrorFor(x => x.Password);
    }

    [Fact]
    public void Validate_ValidLogin_Passes()
    {
        var result = _validator.TestValidate(
            new LoginCommand("admin@test.com", "Password123!", null, null));
        result.ShouldNotHaveAnyValidationErrors();
    }
}

public class LoginCommandHandlerTests
{
    [Fact]
    public async Task Handle_UnknownEmail_ThrowsUnauthorized()
    {
        var ctx = TestContextFactory.Create();
        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());

        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new LoginCommand("nobody@test.com", "Password123!", null, null), CancellationToken.None));
    }

    [Fact]
    public async Task Handle_WrongPassword_IncrementsFailedLoginCount()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User { Email = "a@test.com", NormalizedEmail = "A@TEST.COM", PasswordHash = StubPasswordHasher.KnownHash });
        await ctx.SaveChangesAsync();

        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new LoginCommand("a@test.com", "wrong", null, null), CancellationToken.None));

        Assert.Equal(1, ctx.Users.Single().FailedLoginCount);
    }

    [Fact]
    public async Task Handle_LockedAccount_ThrowsUnauthorized()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User
        {
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            PasswordHash = StubPasswordHasher.KnownHash,
            LockoutEndAt = DateTime.UtcNow.AddMinutes(10),
        });
        await ctx.SaveChangesAsync();

        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new LoginCommand("a@test.com", "Password123!", null, null), CancellationToken.None));
    }

    [Fact]
    public async Task Handle_InactiveUser_ThrowsUnauthorized()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User
        {
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            PasswordHash = StubPasswordHasher.KnownHash,
            IsActive = false,
        });
        await ctx.SaveChangesAsync();

        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());
        await Assert.ThrowsAsync<UnauthorizedAccessException>(
            () => handler.Handle(new LoginCommand("a@test.com", "Password123!", null, null), CancellationToken.None));
    }

    [Fact]
    public async Task Handle_ValidCredentials_ReturnsTokenPairAndRotatesState()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User
        {
            Id = Guid.NewGuid(),
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            PasswordHash = StubPasswordHasher.KnownHash,
            FullName = "Test Admin",
            Role = UserRole.Administrator,
        });
        await ctx.SaveChangesAsync();

        var tokens = new FakeTokenService();
        var handler = new LoginCommandHandler(ctx, tokens, new StubPasswordHasher());
        var response = await handler.Handle(
            new LoginCommand("a@test.com", "Password123!", "10.0.0.1", "agent"), CancellationToken.None);

        Assert.NotNull(response.AccessToken);
        Assert.NotNull(response.RefreshToken);
        Assert.Equal("Administrator", response.Role);

        var user = ctx.Users.Single();
        Assert.NotNull(user.LastLoginAt);
        Assert.Equal("10.0.0.1", user.LastLoginIp);
        Assert.Equal(0, user.FailedLoginCount);

        var stored = ctx.RefreshTokens.Single();
        Assert.Equal(tokens.HashOf(response.RefreshToken), stored.TokenHash);
        Assert.Equal(user.Id, stored.UserId);
    }

    [Fact]
    public async Task Handle_RehashNeeded_RehashesPassword()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User
        {
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            PasswordHash = StubPasswordHasher.RehashHash,
        });
        await ctx.SaveChangesAsync();

        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());
        await handler.Handle(new LoginCommand("a@test.com", "Password123!", null, null), CancellationToken.None);

        Assert.Equal(StubPasswordHasher.KnownHash, ctx.Users.Single().PasswordHash);
    }

    [Fact]
    public async Task Handle_FiveFailedLogins_LocksAccount()
    {
        var ctx = TestContextFactory.Create();
        ctx.Users.Add(new User
        {
            Email = "a@test.com",
            NormalizedEmail = "A@TEST.COM",
            PasswordHash = StubPasswordHasher.KnownHash,
        });
        await ctx.SaveChangesAsync();

        var handler = new LoginCommandHandler(ctx, new FakeTokenService(), new StubPasswordHasher());
        for (var i = 0; i < 5; i++)
        {
            await Assert.ThrowsAsync<UnauthorizedAccessException>(
                () => handler.Handle(new LoginCommand("a@test.com", "wrong", null, null), CancellationToken.None));
        }

        var user = ctx.Users.Single();
        Assert.NotNull(user.LockoutEndAt);
        Assert.True(user.LockoutEndAt > DateTime.UtcNow);
        Assert.Equal(0, user.FailedLoginCount);
    }
}
