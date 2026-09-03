using Clean.Architecture.Core.Common;
using Ardalis.GuardClauses;

namespace Clean.Architecture.Core.Users;

public class Organization : EntityBase<Organization, OrganizationId>, IAuditable, ISoftDelete, IAggregateRoot
{
    private Organization() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public Organization(
        string name = default!,
        string code = default!,
        string? description = default!,
        bool isActive = true,
        string? contactEmail = default!,
        string? contactPhone = default!,
        string? address = default!)  
    {
        Name = Guard.Against.NullOrWhiteSpace(name, nameof(name));
        Code = Guard.Against.NullOrWhiteSpace(code, nameof(code));
        Description = description;
        IsActive = isActive;
        ContactEmail = contactEmail;
        ContactPhone = contactPhone;
        Address = address;
    }

    public string Name { get; private set; } = string.Empty;
    public string Code { get; private set; } = string.Empty;
    public string? Description { get; private set; }
    public bool IsActive { get; private set; } = true;
    public string? ContactEmail { get; private set; }
    public string? ContactPhone { get; private set; }
    public string? Address { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public Organization UpdateName(string newName)
    {
        Name = Guard.Against.NullOrWhiteSpace(newName, nameof(newName));
        return this;
    }

    public Organization UpdateCode(string newCode)
    {
        Code = Guard.Against.NullOrWhiteSpace(newCode, nameof(newCode));
        return this;
    }

    public Organization UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

    public Organization UpdateIsActive(bool newIsActive)
    {
        IsActive = newIsActive;
        return this;
    }

    public Organization UpdateContactEmail(string? newContactEmail)
    {
        ContactEmail = newContactEmail;
        return this;
    }

    public Organization UpdateContactPhone(string? newContactPhone)
    {
        ContactPhone = newContactPhone;
        return this;
    }

    public Organization UpdateAddress(string? newAddress)
    {
        Address = newAddress;
        return this;
    }

}
