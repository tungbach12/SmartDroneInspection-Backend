using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Domain.Ai;
using SmartDroneInspection.Domain.Assets;
using SmartDroneInspection.Domain.Missions;
using SmartDroneInspection.Domain.Planning;
using SmartDroneInspection.Domain.Reports;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.Application.Common.Interfaces;

/// <summary>
/// DbContext surface exposed to Application layer. Application never references
/// Infrastructure directly; each module contributes its DbSet here.
/// </summary>
public interface IApplicationDbContext
{
    DbSet<Organization> Organizations { get; }
    DbSet<User> Users { get; }
    DbSet<RefreshToken> RefreshTokens { get; }
    DbSet<AuditLog> AuditLogs { get; }
    DbSet<AssetCategory> AssetCategories { get; }
    DbSet<SystemSetting> SystemSettings { get; }

    DbSet<Asset> Assets { get; }
    DbSet<AssetDocument> AssetDocuments { get; }
    DbSet<AssetLifecycleLog> AssetLifecycleLogs { get; }

    DbSet<InspectionPlan> InspectionPlans { get; }
    DbSet<PlanAsset> PlanAssets { get; }
    DbSet<InspectionSchedule> InspectionSchedules { get; }
    DbSet<InspectionCalendarEvent> InspectionCalendarEvents { get; }
    DbSet<Notification> Notifications { get; }

    DbSet<InspectionRequest> InspectionRequests { get; }
    DbSet<DroneMission> DroneMissions { get; }
    DbSet<MissionTelemetry> MissionTelemetries { get; }
    DbSet<MissionImage> MissionImages { get; }
    DbSet<MissionFlightLog> MissionFlightLogs { get; }

    DbSet<InspectionReport> InspectionReports { get; }
    DbSet<ReportEvidence> ReportEvidences { get; }
    DbSet<ReportFinding> ReportFindings { get; }
    DbSet<Defect> Defects { get; }
    DbSet<MaintenanceTicket> MaintenanceTickets { get; }
    DbSet<TicketHistory> TicketHistories { get; }
    DbSet<DefectEvidence> DefectEvidences { get; }

    DbSet<AiAnalysisJob> AiAnalysisJobs { get; }
    DbSet<KnowledgeCase> KnowledgeCases { get; }
    DbSet<KnowledgeCaseEmbedding> KnowledgeCaseEmbeddings { get; }

    Task<int> SaveChangesAsync(CancellationToken ct = default);
}
