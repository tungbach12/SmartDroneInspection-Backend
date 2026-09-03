using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Assets.Enums;
using Ardalis.GuardClauses;
using System.Text.Json;

namespace Clean.Architecture.Core.Assets;

public class Asset : EntityBase<Asset, AssetId>, IAuditable, ISoftDelete, IAggregateRoot
{
    private Asset() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public Asset(
        Guid organizationId = default,
        string code = default!,
        string normalizedCode = default!,
        string name = default!,
        string? description = default!,
        Guid? categoryId = default!,
        AssetStatus status = default!,
        double? latitude = default!,
        double? longitude = default!,
        double? altitudeMeters = default!,
        string? address = default!,
        string? region = default!,
        string? countryCode = default!,
        DateTime? installationDate = default!,
        DateTime? lastInspectedAt = default!,
        DateTime? nextInspectionDueAt = default!,
        JsonDocument? metadata = default!,
        JsonDocument? specifications = default!)  
    {
        OrganizationId = Guard.Against.Default(organizationId, nameof(organizationId));
        Code = Guard.Against.NullOrWhiteSpace(code, nameof(code));
        NormalizedCode = Guard.Against.NullOrWhiteSpace(normalizedCode, nameof(normalizedCode));
        Name = Guard.Against.NullOrWhiteSpace(name, nameof(name));
        Description = description;
        CategoryId = categoryId;
        Status = status;
        Latitude = latitude;
        Longitude = longitude;
        AltitudeMeters = altitudeMeters;
        Address = address;
        Region = region;
        CountryCode = countryCode;
        InstallationDate = installationDate;
        LastInspectedAt = lastInspectedAt;
        NextInspectionDueAt = nextInspectionDueAt;
        Metadata = metadata;
        Specifications = specifications;
    }

    public Guid OrganizationId { get; private set; }
    public string Code { get; private set; } = string.Empty;
    public string NormalizedCode { get; private set; } = string.Empty;
    public string Name { get; private set; } = string.Empty;
    public string? Description { get; private set; }
    public Guid? CategoryId { get; private set; }
    public AssetStatus Status { get; private set; } = AssetStatus.Active;
    public double? Latitude { get; private set; }
    public double? Longitude { get; private set; }
    public double? AltitudeMeters { get; private set; }
    public string? Address { get; private set; }
    public string? Region { get; private set; }
    public string? CountryCode { get; private set; }
    public DateTime? InstallationDate { get; private set; }
    public DateTime? LastInspectedAt { get; private set; }
    public DateTime? NextInspectionDueAt { get; private set; }
    public JsonDocument? Metadata { get; private set; }
    public JsonDocument? Specifications { get; private set; }
    public Guid? CreatedBy { get; set; }
    public Guid? UpdatedBy { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }
    public List<string> Tags { get; private set; } = new();

    public Asset UpdateOrganizationId(Guid newOrganizationId)
    {
        OrganizationId = newOrganizationId;
        return this;
    }

    public Asset UpdateCode(string newCode)
    {
        Code = Guard.Against.NullOrWhiteSpace(newCode, nameof(newCode));
        return this;
    }

    public Asset UpdateNormalizedCode(string newNormalizedCode)
    {
        NormalizedCode = Guard.Against.NullOrWhiteSpace(newNormalizedCode, nameof(newNormalizedCode));
        return this;
    }

    public Asset UpdateName(string newName)
    {
        Name = Guard.Against.NullOrWhiteSpace(newName, nameof(newName));
        return this;
    }

    public Asset UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

    public Asset UpdateCategoryId(Guid? newCategoryId)
    {
        CategoryId = newCategoryId;
        return this;
    }

    public Asset UpdateStatus(AssetStatus newStatus)
    {
        Status = newStatus;
        return this;
    }

    public Asset UpdateLatitude(double? newLatitude)
    {
        Latitude = newLatitude;
        return this;
    }

    public Asset UpdateLongitude(double? newLongitude)
    {
        Longitude = newLongitude;
        return this;
    }

    public Asset UpdateAltitudeMeters(double? newAltitudeMeters)
    {
        AltitudeMeters = newAltitudeMeters;
        return this;
    }

    public Asset UpdateAddress(string? newAddress)
    {
        Address = newAddress;
        return this;
    }

    public Asset UpdateRegion(string? newRegion)
    {
        Region = newRegion;
        return this;
    }

    public Asset UpdateCountryCode(string? newCountryCode)
    {
        CountryCode = newCountryCode;
        return this;
    }

    public Asset UpdateInstallationDate(DateTime? newInstallationDate)
    {
        InstallationDate = newInstallationDate;
        return this;
    }

    public Asset UpdateLastInspectedAt(DateTime? newLastInspectedAt)
    {
        LastInspectedAt = newLastInspectedAt;
        return this;
    }

    public Asset UpdateNextInspectionDueAt(DateTime? newNextInspectionDueAt)
    {
        NextInspectionDueAt = newNextInspectionDueAt;
        return this;
    }

    public Asset UpdateMetadata(JsonDocument? newMetadata)
    {
        Metadata = newMetadata;
        return this;
    }

    public Asset UpdateSpecifications(JsonDocument? newSpecifications)
    {
        Specifications = newSpecifications;
        return this;
    }

}
