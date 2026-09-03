using MinimalClean.Architecture.Web.Domain.Ai;
using MinimalClean.Architecture.Web.Domain.Assets;
using MinimalClean.Architecture.Web.Domain.Missions;
using MinimalClean.Architecture.Web.Domain.Planning;
using MinimalClean.Architecture.Web.Domain.Reports;
using MinimalClean.Architecture.Web.Domain.Users;
using Vogen;

namespace MinimalClean.Architecture.Web.Infrastructure.Data.Config;

[EfCoreConverter<AssetId>]
[EfCoreConverter<AssetDocumentId>]
[EfCoreConverter<AssetLifecycleLogId>]
[EfCoreConverter<OrganizationId>]
[EfCoreConverter<UserId>]
[EfCoreConverter<RefreshTokenId>]
[EfCoreConverter<AuditLogId>]
[EfCoreConverter<AssetCategoryId>]
[EfCoreConverter<SystemSettingId>]
[EfCoreConverter<InspectionPlanId>]
[EfCoreConverter<InspectionScheduleId>]
[EfCoreConverter<InspectionCalendarEventId>]
[EfCoreConverter<NotificationId>]
[EfCoreConverter<InspectionRequestId>]
[EfCoreConverter<DroneMissionId>]
[EfCoreConverter<MissionTelemetryId>]
[EfCoreConverter<MissionImageId>]
[EfCoreConverter<MissionFlightLogId>]
[EfCoreConverter<InspectionReportId>]
[EfCoreConverter<ReportEvidenceId>]
[EfCoreConverter<ReportFindingId>]
[EfCoreConverter<DefectId>]
[EfCoreConverter<MaintenanceTicketId>]
[EfCoreConverter<TicketHistoryId>]
[EfCoreConverter<DefectEvidenceId>]
[EfCoreConverter<AiAnalysisJobId>]
[EfCoreConverter<KnowledgeCaseId>]
[EfCoreConverter<KnowledgeCaseEmbeddingId>]
internal partial class VogenEfCoreConverters;
