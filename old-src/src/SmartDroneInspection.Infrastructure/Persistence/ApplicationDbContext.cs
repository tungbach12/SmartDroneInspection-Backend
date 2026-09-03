using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Ai;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Common;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Infrastructure.Persistence;

public class ApplicationDbContext(
    DbContextOptions<ApplicationDbContext> options,
    ICurrentUserService currentUser) : DbContext(options), IApplicationDbContext
{
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
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(ApplicationDbContext).Assembly);

        foreach (var entityType in modelBuilder.Model.GetEntityTypes())
        {
            if (!typeof(ISoftDelete).IsAssignableFrom(entityType.ClrType))
            {
                continue;
            }

            var method = typeof(ApplicationDbContext)
                .GetMethod(nameof(ApplySoftDeleteFilter), System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.NonPublic)!
                .MakeGenericMethod(entityType.ClrType);
            method.Invoke(null, [modelBuilder]);
        }
    }

    public override async Task<int> SaveChangesAsync(CancellationToken ct = default)
    {
        var utcNow = DateTime.UtcNow;
        var userId = currentUser.UserId;

        foreach (var entry in ChangeTracker.Entries<BaseEntity>())
        {
            if (entry.State == EntityState.Added)
            {
                entry.Entity.CreatedAt = utcNow;
                if (entry.Entity is IAuditable added)
                {
                    added.CreatedBy = userId;
                }
            }
            else if (entry.State == EntityState.Modified)
            {
                entry.Entity.UpdatedAt = utcNow;
                if (entry.Entity is IAuditable modified)
                {
                    modified.UpdatedBy = userId;
                }

                if (entry.Entity is IHasVersion versioned)
                {
                    versioned.Version += 1;
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
            entry.Entity.DeletedBy = userId;
        }

        return await base.SaveChangesAsync(ct);
    }

    private static void ApplySoftDeleteFilter<TEntity>(ModelBuilder modelBuilder)
        where TEntity : class, ISoftDelete
    {
        modelBuilder.Entity<TEntity>().HasQueryFilter(entity => !entity.IsDeleted);
    }
}
