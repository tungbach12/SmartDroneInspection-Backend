namespace SmartDroneInspection.Domain.Common;

/// <summary>System roles for RBAC. Matches spec section 2.</summary>
public static class Roles
{
    public const string Administrator = "Administrator";
    public const string InspectionManager = "InspectionManager";
    public const string Inspector = "Inspector";
    public const string MaintenanceEngineer = "MaintenanceEngineer";
    public const string Viewer = "Viewer";

    public static readonly string[] All =
    [
        Administrator,
        InspectionManager,
        Inspector,
        MaintenanceEngineer,
        Viewer,
    ];
}
