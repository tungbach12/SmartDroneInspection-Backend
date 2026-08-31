using Microsoft.EntityFrameworkCore;
using SmartDroneInspection.Application.Common.Interfaces;
using SmartDroneInspection.Domain.Users;

namespace SmartDroneInspection.UnitTests.Common;

/// <summary>Sequenced token stub: minted raw tokens are tracked so hash lookups work.</summary>
public sealed class FakeTokenService : ITokenService
{
    private readonly Dictionary<string, string> _hashByRaw = new();
    private int _sequence;

    public (string Token, string JwtId, DateTime ExpiresAtUtc) CreateAccessToken(Guid userId, string email, string role)
        => ($"access-{userId}", Guid.NewGuid().ToString(), DateTime.UtcNow.AddMinutes(60));

    public (string RawToken, string Hash) CreateRefreshToken()
    {
        var raw = $"refresh-{Interlocked.Increment(ref _sequence)}";
        var hash = HashToken(raw);
        _hashByRaw[raw] = hash;
        return (raw, hash);
    }

    public string HashToken(string rawToken) =>
        Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(
            System.Text.Encoding.UTF8.GetBytes(rawToken)));

    /// <summary>Hash for a raw token minted by this instance; unknown tokens hash deterministically.</summary>
    public string HashOf(string rawToken) =>
        _hashByRaw.TryGetValue(rawToken, out var hash) ? hash : HashToken(rawToken);
}

public sealed class StubPasswordHasher : IPasswordHasher
{
    public const string KnownHash = "known-hash";
    public const string RehashHash = "rehash-hash";
    public const string CorrectPassword = "Password123!";

    public string HashPassword(string password) => KnownHash;

    public PasswordVerification VerifyPassword(string passwordHash, string providedPassword)
    {
        if (providedPassword != CorrectPassword)
        {
            return PasswordVerification.Failed;
        }

        return passwordHash == KnownHash ? PasswordVerification.Success
            : passwordHash == RehashHash ? PasswordVerification.SuccessRehashNeeded
            : PasswordVerification.Failed;
    }
}

/// <summary>In-memory EF Core context implementing IApplicationDbContext for handler tests.</summary>
public sealed class TestDbContext : DbContext, IApplicationDbContext
{
    public TestDbContext(DbContextOptions<TestDbContext> options) : base(options) { }

    // The AI entities are excluded from the test model: KnowledgeCaseEmbedding maps an
    // EmbeddingVector value object that EF InMemory cannot construct. No handler under
    // test touches them, so they are ignored here.
    DbSet<Domain.Ai.KnowledgeCase> IApplicationDbContext.KnowledgeCases =>
        throw new NotSupportedException("AI entities are not part of the unit-test model.");
    DbSet<Domain.Ai.KnowledgeCaseEmbedding> IApplicationDbContext.KnowledgeCaseEmbeddings =>
        throw new NotSupportedException("AI entities are not part of the unit-test model.");
    DbSet<Domain.Ai.AiAnalysisJob> IApplicationDbContext.AiAnalysisJobs =>
        throw new NotSupportedException("AI entities are not part of the unit-test model.");

    public DbSet<Domain.Assets.Asset> Assets => Set<Domain.Assets.Asset>();
    public DbSet<Domain.Assets.AssetDocument> AssetDocuments => Set<Domain.Assets.AssetDocument>();
    public DbSet<Domain.Assets.AssetLifecycleLog> AssetLifecycleLogs => Set<Domain.Assets.AssetLifecycleLog>();
    public DbSet<Domain.Users.AssetCategory> AssetCategories => Set<Domain.Users.AssetCategory>();
    public DbSet<Domain.Users.SystemSetting> SystemSettings => Set<Domain.Users.SystemSetting>();

    public DbSet<Domain.Planning.InspectionPlan> InspectionPlans => Set<Domain.Planning.InspectionPlan>();
    public DbSet<Domain.Planning.PlanAsset> PlanAssets => Set<Domain.Planning.PlanAsset>();
    public DbSet<Domain.Planning.InspectionSchedule> InspectionSchedules => Set<Domain.Planning.InspectionSchedule>();
    public DbSet<Domain.Planning.InspectionCalendarEvent> InspectionCalendarEvents => Set<Domain.Planning.InspectionCalendarEvent>();
    public DbSet<Domain.Planning.Notification> Notifications => Set<Domain.Planning.Notification>();

    public DbSet<Domain.Missions.InspectionRequest> InspectionRequests => Set<Domain.Missions.InspectionRequest>();
    public DbSet<Domain.Missions.DroneMission> DroneMissions => Set<Domain.Missions.DroneMission>();
    public DbSet<Domain.Missions.MissionTelemetry> MissionTelemetries => Set<Domain.Missions.MissionTelemetry>();
    public DbSet<Domain.Missions.MissionImage> MissionImages => Set<Domain.Missions.MissionImage>();
    public DbSet<Domain.Missions.MissionFlightLog> MissionFlightLogs => Set<Domain.Missions.MissionFlightLog>();

    public DbSet<Domain.Reports.InspectionReport> InspectionReports => Set<Domain.Reports.InspectionReport>();
    public DbSet<Domain.Reports.ReportEvidence> ReportEvidences => Set<Domain.Reports.ReportEvidence>();
    public DbSet<Domain.Reports.ReportFinding> ReportFindings => Set<Domain.Reports.ReportFinding>();
    public DbSet<Domain.Reports.Defect> Defects => Set<Domain.Reports.Defect>();
    public DbSet<Domain.Reports.MaintenanceTicket> MaintenanceTickets => Set<Domain.Reports.MaintenanceTicket>();
    public DbSet<Domain.Reports.TicketHistory> TicketHistories => Set<Domain.Reports.TicketHistory>();
    public DbSet<Domain.Reports.DefectEvidence> DefectEvidences => Set<Domain.Reports.DefectEvidence>();

    public DbSet<Domain.Users.Organization> Organizations => Set<Domain.Users.Organization>();
    public DbSet<Domain.Users.User> Users => Set<Domain.Users.User>();
    public DbSet<Domain.Users.RefreshToken> RefreshTokens => Set<Domain.Users.RefreshToken>();
    public DbSet<Domain.Users.AuditLog> AuditLogs => Set<Domain.Users.AuditLog>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // EF InMemory cannot construct JsonDocument (value objects with no bindable
        // constructor). Entities carrying JsonDocument properties are outside the
        // handlers under test, so keep them out of the test model entirely.
        modelBuilder.Ignore<Domain.Ai.KnowledgeCase>();
        modelBuilder.Ignore<Domain.Ai.KnowledgeCaseEmbedding>();
        modelBuilder.Ignore<Domain.Ai.AiAnalysisJob>();
        modelBuilder.Ignore<Domain.Missions.DroneMission>();
        modelBuilder.Ignore<Domain.Missions.MissionFlightLog>();
        modelBuilder.Ignore<Domain.Reports.ReportFinding>();
        modelBuilder.Ignore<Domain.Users.AuditLog>();
        modelBuilder.Ignore<Domain.Users.SystemSetting>();

        // Asset is used by handler tests, but its JsonDocument metadata columns
        // have the same InMemory limitation — exclude just those properties.
        modelBuilder.Entity<Domain.Assets.Asset>()
            .Ignore(a => a.Metadata)
            .Ignore(a => a.Specifications);

        // Composite-key join tables: configure the key InMemory would otherwise miss.
        modelBuilder.Entity<Domain.Planning.PlanAsset>()
            .HasKey(p => new { p.PlanId, p.AssetId });
    }

    public override Task<int> SaveChangesAsync(CancellationToken ct = default) => base.SaveChangesAsync(ct);
}

public static class TestContextFactory
{
    public static TestDbContext Create(string? name = null)
    {
        var options = new DbContextOptionsBuilder<TestDbContext>()
            .UseInMemoryDatabase(name ?? Guid.NewGuid().ToString())
            .Options;
        return new TestDbContext(options);
    }
}
