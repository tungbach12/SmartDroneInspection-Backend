using SmartDroneInspection.UnitTests.Common;
using FluentValidation.TestHelper;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Application.Assets.Commands;

namespace SmartDroneInspection.UnitTests.Assets.Commands;

public class CreateAssetCommandValidatorTests
{
    private readonly CreateAssetCommandValidator _validator = new();

    [Fact]
    public void Validate_EmptyName_Fails()
    {
        var cmd = new CreateAssetCommand("", "A-001", null, null, Guid.NewGuid(), null, null, null, null);
        var result = _validator.TestValidate(cmd);
        result.ShouldHaveValidationErrorFor(x => x.Name);
    }

    [Theory]
    [InlineData(-91)]
    [InlineData(91)]
    public void Validate_LatitudeOutOfRange_Fails(double latitude)
    {
        var cmd = new CreateAssetCommand("Asset", "A-001", null, null, Guid.NewGuid(), null, null, latitude, null);
        var result = _validator.TestValidate(cmd);
        result.ShouldHaveValidationErrorFor(x => x.Latitude);
    }

    [Fact]
    public void Validate_ValidCommand_Passes()
    {
        var cmd = new CreateAssetCommand(
            "Transformer 1", "TR-001", "Main transformer", null, Guid.NewGuid(),
            "Hanoi", "North", 21.0285, 105.8542);
        var result = _validator.TestValidate(cmd);
        result.ShouldNotHaveAnyValidationErrors();
    }
}

public class CreateAssetCommandHandlerTests
{
    [Fact]
    public async Task Handle_DuplicateCodeInSameOrg_ThrowsInvalidOperation()
    {
        var ctx = TestContextFactory.Create();
        var orgId = Guid.NewGuid();
        ctx.Assets.Add(new Domain.Assets.Asset
        {
            OrganizationId = orgId,
            Code = "TR-001",
            NormalizedCode = "TR-001",
        });
        await ctx.SaveChangesAsync();

        var handler = new CreateAssetCommandHandler(ctx);
        var cmd = new CreateAssetCommand("Transformer 1", "tr-001", null, null, orgId, null, null, null, null);

        var ex = await Assert.ThrowsAsync<InvalidOperationException>(
            () => handler.Handle(cmd, CancellationToken.None));
        Assert.Contains("tr-001", ex.Message);
    }

    [Fact]
    public async Task Handle_UniqueCode_ReturnsAssetDto()
    {
        var ctx = TestContextFactory.Create();
        var handler = new CreateAssetCommandHandler(ctx);
        var orgId = Guid.NewGuid();
        var cmd = new CreateAssetCommand(
            "Transformer 1", "TR-001", "Main", null, orgId, "Hanoi", "North", 21.02, 105.85);

        var dto = await handler.Handle(cmd, CancellationToken.None);

        Assert.Equal("TR-001", dto.Code);
        var saved = ctx.Assets.Single();
        Assert.Equal("TR-001", saved.NormalizedCode);
        Assert.Equal(orgId, saved.OrganizationId);
    }
}
