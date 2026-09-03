using Ardalis.SmartEnum;

namespace MinimalClean.Architecture.Web.Domain.Users.Enums;

public sealed class UserRole : SmartEnum<UserRole>
{
    public static readonly UserRole Administrator = new(nameof(Administrator), 0);
    public static readonly UserRole InspectionManager = new(nameof(InspectionManager), 1);
    public static readonly UserRole Inspector = new(nameof(Inspector), 2);
    public static readonly UserRole MaintenanceEngineer = new(nameof(MaintenanceEngineer), 3);
    public static readonly UserRole Viewer = new(nameof(Viewer), 4);

    private UserRole(string name, int value) : base(name, value) { }
}
