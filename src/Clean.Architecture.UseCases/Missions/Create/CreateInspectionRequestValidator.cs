using FluentValidation;

namespace Clean.Architecture.UseCases.Missions.Create;

public class CreateInspectionRequestValidator : AbstractValidator<CreateInspectionRequestCommand>
{
    public CreateInspectionRequestValidator()
    {
        RuleFor(x => x.Title).NotEmpty().MaximumLength(250);
        RuleFor(x => x.Description).NotEmpty();
        RuleFor(x => x.AssetId).NotEmpty();
        RuleFor(x => x.OrganizationId).NotEmpty();
    }
}
