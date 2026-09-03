using MinimalClean.Architecture.Web.Domain.Common;
using Ardalis.GuardClauses;

namespace MinimalClean.Architecture.Web.Domain.Users;

public class AssetCategory : EntityBase<AssetCategory, AssetCategoryId>, ISoftDelete, IAggregateRoot
{
    private AssetCategory() { } // EF Core ctor

    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }

    public AssetCategory(
        string name = default!,
        int sortOrder = default,
        string? description = default!,
        Guid? parentId = default!,
        string? iconUrl = default!,
        bool isActive = true)  
    {
        Name = Guard.Against.NullOrWhiteSpace(name, nameof(name));
        SortOrder = sortOrder;
        Description = description;
        ParentId = parentId;
        IconUrl = iconUrl;
        IsActive = isActive;
    }

    public string Name { get; private set; } = string.Empty;
    public string? Description { get; private set; }
    public Guid? ParentId { get; private set; }
    public string? IconUrl { get; private set; }
    public int SortOrder { get; private set; }
    public bool IsActive { get; private set; } = true;
    public bool IsDeleted { get; set; }
    public DateTime? DeletedAt { get; set; }
    public Guid? DeletedBy { get; set; }

    public AssetCategory UpdateName(string newName)
    {
        Name = Guard.Against.NullOrWhiteSpace(newName, nameof(newName));
        return this;
    }

    public AssetCategory UpdateDescription(string? newDescription)
    {
        Description = newDescription;
        return this;
    }

    public AssetCategory UpdateParentId(Guid? newParentId)
    {
        ParentId = newParentId;
        return this;
    }

    public AssetCategory UpdateIconUrl(string? newIconUrl)
    {
        IconUrl = newIconUrl;
        return this;
    }

    public AssetCategory UpdateSortOrder(int newSortOrder)
    {
        SortOrder = newSortOrder;
        return this;
    }

    public AssetCategory UpdateIsActive(bool newIsActive)
    {
        IsActive = newIsActive;
        return this;
    }

}
