using Clean.Architecture.Core.Ai;
using Clean.Architecture.Core.Assets;
using Clean.Architecture.Core.ContributorAggregate;
using Clean.Architecture.Core.Missions;
using Clean.Architecture.Core.Planning;
using Clean.Architecture.Core.Reports;
using Clean.Architecture.Core.Users;
using Vogen;

namespace Clean.Architecture.Infrastructure.Data.Config;

[EfCoreConverter<ContributorId>]
[EfCoreConverter<ContributorName>]
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