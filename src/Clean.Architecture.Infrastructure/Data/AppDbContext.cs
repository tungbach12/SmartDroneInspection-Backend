using Clean.Architecture.Core.Ai;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.Core.Common;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Planning;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Core.Users;
using Microsoft.EntityFrameworkCore;

namespace Clean.Architecture.Infrastructure.Data;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    // SmartDrone — preserve tables/fields
    public DbSet<Organization> Organizations => Set<Organization>();
    public DbSet<User> Users => Set<User>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<AuditLog> AuditLogs => Set<AuditLog>();
    public DbSet<AssetCategory> AssetCategories => Set<AssetCategory>();
    public DbSet<SystemSetting> SystemSettings => Set<SystemSetting>();

    public DbSet<Asset> Assets => Set<Asset>();
    public DbSet<AssetDocument> AssetDocuments => Set<AssetDocument>();
    public DbSet<AssetLifecycleLog> AssetLifecycleLogs => Set<AssetLifecycleLog>();

    public DbSet<InspectionPlan> InspectionPlans => Set<InspectionPlan>();
    public DbSet<PlanAsset> PlanAssets => Set<PlanAsset>();
    public DbSet<InspectionSchedule> InspectionSchedules => Set<InspectionSchedule>();
    public DbSet<InspectionCalendarEvent> InspectionCalendarEvents => Set<InspectionCalendarEvent>();
    public DbSet<Notification> Notifications => Set<Notification>();

    public DbSet<InspectionRequest> InspectionRequests => Set<InspectionRequest>();
    public DbSet<DroneMission> DroneMissions => Set<DroneMission>();
    public DbSet<MissionTelemetry> MissionTelemetries => Set<MissionTelemetry>();
    public DbSet<MissionImage> MissionImages => Set<MissionImage>();
    public DbSet<MissionFlightLog> MissionFlightLogs => Set<MissionFlightLog>();

    public DbSet<InspectionReport> InspectionReports => Set<InspectionReport>();
    public DbSet<ReportEvidence> ReportEvidences => Set<ReportEvidence>();
    public DbSet<ReportFinding> ReportFindings => Set<ReportFinding>();
    public DbSet<Defect> Defects => Set<Defect>();
    public DbSet<MaintenanceTicket> MaintenanceTickets => Set<MaintenanceTicket>();
    public DbSet<TicketHistory> TicketHistories => Set<TicketHistory>();
    public DbSet<DefectEvidence> DefectEvidences => Set<DefectEvidence>();

    public DbSet<AiAnalysisJob> AiAnalysisJobs => Set<AiAnalysisJob>();
    public DbSet<KnowledgeCase> KnowledgeCases => Set<KnowledgeCase>();
    public DbSet<KnowledgeCaseEmbedding> KnowledgeCaseEmbeddings => Set<KnowledgeCaseEmbedding>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);
        modelBuilder.HasPostgresExtension("vector");
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(AppDbContext).Assembly);

        foreach (var entityType in modelBuilder.Model.GetEntityTypes())
        {
            if (!typeof(ISoftDelete).IsAssignableFrom(entityType.ClrType))
            {
                continue;
            }

            var method = typeof(AppDbContext)
                .GetMethod(nameof(ApplySoftDeleteFilter), System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.NonPublic)!
                .MakeGenericMethod(entityType.ClrType);
            method.Invoke(null, [modelBuilder]);
        }
    }

    public override async Task<int> SaveChangesAsync(CancellationToken ct = default)
    {
        var utcNow = DateTime.UtcNow;

        foreach (var entry in ChangeTracker.Entries())
        {
            var entity = entry.Entity;
            var entityType = entity.GetType();

            // Timestamps � preserve CreatedAt/UpdatedAt columns
            var createdAtProp = entityType.GetProperty("CreatedAt");
            var updatedAtProp = entityType.GetProperty("UpdatedAt");

            if (entry.State == EntityState.Added && createdAtProp != null && createdAtProp.CanWrite)
            {
                createdAtProp.SetValue(entity, utcNow);
            }
            else if (entry.State == EntityState.Modified && updatedAtProp != null && updatedAtProp.CanWrite)
            {
                updatedAtProp.SetValue(entity, utcNow);
            }

            if (entry.State == EntityState.Modified)
            {
                var versionProp = entityType.GetProperty("Version");
                if (versionProp != null && versionProp.CanWrite && versionProp.PropertyType == typeof(int))
                {
                    var current = (int)versionProp.GetValue(entity)!;
                    versionProp.SetValue(entity, current + 1);
                }
            }
        }

        foreach (var entry in ChangeTracker.Entries<ISoftDelete>())
        {
            if (entry.State != EntityState.Deleted)
            {
                continue;
            }

            entry.State = EntityState.Modified;
            entry.Entity.IsDeleted = true;
            entry.Entity.DeletedAt = utcNow;
            // DeletedBy would be set via ICurrentUserService if available
        }

        return await base.SaveChangesAsync(ct);
    }

    public override int SaveChanges() =>
        SaveChangesAsync().GetAwaiter().GetResult();

    private static void ApplySoftDeleteFilter<TEntity>(ModelBuilder modelBuilder)
        where TEntity : class, ISoftDelete
    {
        modelBuilder.Entity<TEntity>().HasQueryFilter(entity => !entity.IsDeleted);
    }
}
