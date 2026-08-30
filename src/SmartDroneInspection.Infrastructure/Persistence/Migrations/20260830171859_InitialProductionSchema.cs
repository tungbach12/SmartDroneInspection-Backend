using System;
using System.Text.Json;
using Microsoft.EntityFrameworkCore.Migrations;
using Pgvector;

#nullable disable

namespace SmartDroneInspection.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class InitialProductionSchema : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterDatabase()
                .Annotation("Npgsql:PostgresExtension:vector", ",,");

            migrationBuilder.CreateTable(
                name: "asset_categories",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    parent_id = table.Column<Guid>(type: "uuid", nullable: true),
                    icon_url = table.Column<string>(type: "character varying(2048)", maxLength: 2048, nullable: true),
                    sort_order = table.Column<int>(type: "integer", nullable: false, defaultValue: 0),
                    is_active = table.Column<bool>(type: "boolean", nullable: false),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_asset_categories", x => x.id);
                    table.ForeignKey(
                        name: "fk_asset_categories_asset_categories_parent_id",
                        column: x => x.parent_id,
                        principalTable: "asset_categories",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "organizations",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    code = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    is_active = table.Column<bool>(type: "boolean", nullable: false),
                    contact_email = table.Column<string>(type: "character varying(320)", maxLength: 320, nullable: true),
                    contact_phone = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: true),
                    address = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_organizations", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "assets",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    code = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    normalized_code = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    name = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    category_id = table.Column<Guid>(type: "uuid", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    latitude = table.Column<double>(type: "double precision", nullable: true),
                    longitude = table.Column<double>(type: "double precision", nullable: true),
                    altitude_meters = table.Column<double>(type: "double precision", nullable: true),
                    address = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    region = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: true),
                    country_code = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: true),
                    installation_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    last_inspected_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    next_inspection_due_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    metadata = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    specifications = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    tags = table.Column<string[]>(type: "text[]", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_assets", x => x.id);
                    table.CheckConstraint("ck_assets_coordinates", "(latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)");
                    table.CheckConstraint("ck_assets_country_code", "country_code IS NULL OR country_code ~ '^[A-Z]{2}$'");
                    table.ForeignKey(
                        name: "fk_assets_asset_categories_category_id",
                        column: x => x.category_id,
                        principalTable: "asset_categories",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_assets_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "users",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: true),
                    email = table.Column<string>(type: "character varying(320)", maxLength: 320, nullable: false),
                    normalized_email = table.Column<string>(type: "character varying(320)", maxLength: 320, nullable: false),
                    username = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    normalized_username = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    password_hash = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false),
                    full_name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    phone = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: true),
                    role = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    is_active = table.Column<bool>(type: "boolean", nullable: false, defaultValue: true),
                    last_login_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    last_login_ip = table.Column<string>(type: "character varying(45)", maxLength: 45, nullable: true),
                    password_changed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    must_change_password = table.Column<bool>(type: "boolean", nullable: false),
                    failed_login_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 0),
                    lockout_end_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    avatar_url = table.Column<string>(type: "character varying(2048)", maxLength: 2048, nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_users", x => x.id);
                    table.CheckConstraint("ck_users_failed_login_count", "failed_login_count >= 0");
                    table.ForeignKey(
                        name: "fk_users_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "asset_documents",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    file_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: false),
                    file_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    file_size_bytes = table.Column<long>(type: "bigint", nullable: true),
                    mime_type = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                    uploaded_by = table.Column<Guid>(type: "uuid", nullable: false),
                    document_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_asset_documents", x => x.id);
                    table.CheckConstraint("ck_asset_documents_size", "file_size_bytes IS NULL OR file_size_bytes >= 0");
                    table.ForeignKey(
                        name: "fk_asset_documents_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_asset_documents_users_uploaded_by",
                        column: x => x.uploaded_by,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "asset_lifecycle_logs",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    from_status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: true),
                    to_status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    changed_by = table.Column<Guid>(type: "uuid", nullable: true),
                    changed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    reason = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    note = table.Column<string>(type: "text", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_asset_lifecycle_logs", x => x.id);
                    table.ForeignKey(
                        name: "fk_asset_lifecycle_logs_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_asset_lifecycle_logs_users_changed_by",
                        column: x => x.changed_by,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "audit_logs",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    action = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    entity_type = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    entity_id = table.Column<Guid>(type: "uuid", nullable: true),
                    category = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    old_values = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    new_values = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    ip_address = table.Column<string>(type: "character varying(45)", maxLength: 45, nullable: true),
                    user_agent = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    correlation_id = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    occurred_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_audit_logs", x => x.id);
                    table.ForeignKey(
                        name: "fk_audit_logs_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "inspection_plans",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    created_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    frequency_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    frequency_interval = table.Column<int>(type: "integer", nullable: false, defaultValue: 1),
                    priority = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    start_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    end_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    next_run_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    last_run_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    activated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    activated_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    paused_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    paused_reason = table.Column<string>(type: "text", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_inspection_plans", x => x.id);
                    table.CheckConstraint("ck_inspection_plans_date_range", "end_date IS NULL OR end_date >= start_date");
                    table.CheckConstraint("ck_inspection_plans_frequency_interval", "frequency_interval >= 1");
                    table.ForeignKey(
                        name: "fk_inspection_plans_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_plans_users_activated_by_user_id",
                        column: x => x.activated_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_plans_users_created_by_user_id",
                        column: x => x.created_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "notifications",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    message = table.Column<string>(type: "text", nullable: false),
                    type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    category = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    ref_entity_type = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    ref_entity_id = table.Column<Guid>(type: "uuid", nullable: true),
                    action_url = table.Column<string>(type: "character varying(2048)", maxLength: 2048, nullable: true),
                    is_read = table.Column<bool>(type: "boolean", nullable: false),
                    read_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    expires_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    sent_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    delivery_channel = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    delivery_status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    idempotency_key = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_notifications", x => x.id);
                    table.ForeignKey(
                        name: "fk_notifications_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "refresh_tokens",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    token_hash = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    jwt_id = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    expires_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    revoked_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    revoked_reason = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    replaced_by_token_id = table.Column<Guid>(type: "uuid", nullable: true),
                    user_agent = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    ip_address = table.Column<string>(type: "character varying(45)", maxLength: 45, nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_refresh_tokens", x => x.id);
                    table.CheckConstraint("ck_refresh_tokens_expiry", "expires_at > created_at");
                    table.ForeignKey(
                        name: "fk_refresh_tokens_refresh_tokens_replaced_by_token_id",
                        column: x => x.replaced_by_token_id,
                        principalTable: "refresh_tokens",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_refresh_tokens_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "system_settings",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    key = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    value = table.Column<JsonDocument>(type: "jsonb", nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    version = table.Column<int>(type: "integer", nullable: false, defaultValue: 1),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_system_settings", x => x.id);
                    table.ForeignKey(
                        name: "fk_system_settings_users_updated_by",
                        column: x => x.updated_by,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "inspection_requests",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    requested_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    inspector_id = table.Column<Guid>(type: "uuid", nullable: true),
                    plan_id = table.Column<Guid>(type: "uuid", nullable: true),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: false),
                    priority = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    decided_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    decided_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    reject_reason = table.Column<string>(type: "text", nullable: true),
                    latitude = table.Column<double>(type: "double precision", nullable: true),
                    longitude = table.Column<double>(type: "double precision", nullable: true),
                    location_override = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    requested_completion_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    actual_completion_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    mission_creation_key = table.Column<Guid>(type: "uuid", nullable: true),
                    estimated_duration_minutes = table.Column<int>(type: "integer", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_inspection_requests", x => x.id);
                    table.CheckConstraint("ck_inspection_requests_coordinates", "(latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)");
                    table.CheckConstraint("ck_inspection_requests_duration", "estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0");
                    table.ForeignKey(
                        name: "fk_inspection_requests_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_requests_inspection_plans_plan_id",
                        column: x => x.plan_id,
                        principalTable: "inspection_plans",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_requests_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_requests_users_decided_by_user_id",
                        column: x => x.decided_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_requests_users_inspector_id",
                        column: x => x.inspector_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_requests_users_requested_by_user_id",
                        column: x => x.requested_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "inspection_schedules",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    plan_id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    scheduled_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    scheduled_end_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    inspector_id = table.Column<Guid>(type: "uuid", nullable: true),
                    assigned_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    assigned_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    completed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    cancelled_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    cancelled_reason = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    rescheduled_from_id = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_inspection_schedules", x => x.id);
                    table.CheckConstraint("ck_inspection_schedules_date_range", "scheduled_end_date IS NULL OR scheduled_end_date >= scheduled_date");
                    table.ForeignKey(
                        name: "fk_inspection_schedules_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_schedules_inspection_plans_plan_id",
                        column: x => x.plan_id,
                        principalTable: "inspection_plans",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_schedules_inspection_schedules_rescheduled_from_",
                        column: x => x.rescheduled_from_id,
                        principalTable: "inspection_schedules",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_schedules_users_assigned_by_user_id",
                        column: x => x.assigned_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_schedules_users_inspector_id",
                        column: x => x.inspector_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "plan_assets",
                columns: table => new
                {
                    plan_id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    sort_order = table.Column<int>(type: "integer", nullable: false, defaultValue: 0)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_plan_assets", x => new { x.plan_id, x.asset_id });
                    table.ForeignKey(
                        name: "fk_plan_assets_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_plan_assets_inspection_plans_plan_id",
                        column: x => x.plan_id,
                        principalTable: "inspection_plans",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "drone_missions",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    inspection_request_id = table.Column<Guid>(type: "uuid", nullable: false),
                    smart_drone_hub_mission_id = table.Column<Guid>(type: "uuid", nullable: true),
                    external_status_code = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    mission_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    notes = table.Column<string>(type: "text", nullable: true),
                    planned_altitude_meters = table.Column<double>(type: "double precision", nullable: true),
                    planned_distance_meters = table.Column<double>(type: "double precision", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    created_via = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    launched_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    launched_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    started_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    completed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    cancelled_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    cancelled_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    cancel_reason = table.Column<string>(type: "text", nullable: true),
                    failure_reason = table.Column<string>(type: "text", nullable: true),
                    total_distance_meters = table.Column<double>(type: "double precision", nullable: true),
                    total_flight_time_seconds = table.Column<int>(type: "integer", nullable: true),
                    max_altitude_meters = table.Column<double>(type: "double precision", nullable: true),
                    max_battery_used_percent = table.Column<int>(type: "integer", nullable: true),
                    weather_conditions = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    last_synced_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    version = table.Column<int>(type: "integer", nullable: false, defaultValue: 1),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_drone_missions", x => x.id);
                    table.CheckConstraint("ck_drone_missions_values", "(planned_altitude_meters IS NULL OR planned_altitude_meters >= 0) AND (planned_distance_meters IS NULL OR planned_distance_meters >= 0) AND (total_distance_meters IS NULL OR total_distance_meters >= 0) AND (total_flight_time_seconds IS NULL OR total_flight_time_seconds >= 0) AND (max_altitude_meters IS NULL OR max_altitude_meters >= 0) AND (max_battery_used_percent IS NULL OR max_battery_used_percent BETWEEN 0 AND 100)");
                    table.ForeignKey(
                        name: "fk_drone_missions_inspection_requests_inspection_request_id",
                        column: x => x.inspection_request_id,
                        principalTable: "inspection_requests",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_drone_missions_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_drone_missions_users_cancelled_by_user_id",
                        column: x => x.cancelled_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_drone_missions_users_launched_by_user_id",
                        column: x => x.launched_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "inspection_calendar_events",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    plan_id = table.Column<Guid>(type: "uuid", nullable: true),
                    request_id = table.Column<Guid>(type: "uuid", nullable: true),
                    schedule_id = table.Column<Guid>(type: "uuid", nullable: true),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: true),
                    event_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    end_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    all_day = table.Column<bool>(type: "boolean", nullable: false),
                    location = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    recurrence_rule = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    recurrence_parent_id = table.Column<Guid>(type: "uuid", nullable: true),
                    created_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_inspection_calendar_events", x => x.id);
                    table.ForeignKey(
                        name: "fk_inspection_calendar_events_inspection_calendar_events_recur",
                        column: x => x.recurrence_parent_id,
                        principalTable: "inspection_calendar_events",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_calendar_events_inspection_plans_plan_id",
                        column: x => x.plan_id,
                        principalTable: "inspection_plans",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_calendar_events_inspection_requests_request_id",
                        column: x => x.request_id,
                        principalTable: "inspection_requests",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_calendar_events_inspection_schedules_schedule_id",
                        column: x => x.schedule_id,
                        principalTable: "inspection_schedules",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_calendar_events_users_created_by_user_id",
                        column: x => x.created_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "inspection_reports",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    inspection_request_id = table.Column<Guid>(type: "uuid", nullable: false),
                    mission_id = table.Column<Guid>(type: "uuid", nullable: true),
                    inspector_id = table.Column<Guid>(type: "uuid", nullable: false),
                    report_number = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: true),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    summary = table.Column<string>(type: "text", nullable: true),
                    summary_generated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    summary_model_version = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    findings = table.Column<string>(type: "text", nullable: false),
                    recommendations = table.Column<string>(type: "text", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    submitted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    rejected_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    rejected_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    reject_reason = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    reviewed_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    reviewed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    review_comment = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    version = table.Column<int>(type: "integer", nullable: false, defaultValue: 1),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_inspection_reports", x => x.id);
                    table.ForeignKey(
                        name: "fk_inspection_reports_drone_missions_mission_id",
                        column: x => x.mission_id,
                        principalTable: "drone_missions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_reports_inspection_requests_inspection_request_id",
                        column: x => x.inspection_request_id,
                        principalTable: "inspection_requests",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_reports_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_reports_users_inspector_id",
                        column: x => x.inspector_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_inspection_reports_users_rejected_by_user_id",
                        column: x => x.rejected_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_inspection_reports_users_reviewed_by_user_id",
                        column: x => x.reviewed_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "mission_flight_logs",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    drone_mission_id = table.Column<Guid>(type: "uuid", nullable: false),
                    sequence_number = table.Column<long>(type: "bigint", nullable: false),
                    log_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    severity = table.Column<int>(type: "integer", nullable: false),
                    content = table.Column<JsonDocument>(type: "jsonb", nullable: false),
                    logged_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_mission_flight_logs", x => x.id);
                    table.CheckConstraint("ck_mission_flight_logs_values", "sequence_number >= 0 AND severity BETWEEN 1 AND 5");
                    table.ForeignKey(
                        name: "fk_mission_flight_logs_drone_missions_drone_mission_id",
                        column: x => x.drone_mission_id,
                        principalTable: "drone_missions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "mission_images",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    drone_mission_id = table.Column<Guid>(type: "uuid", nullable: false),
                    minio_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: false),
                    thumbnail_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    file_size_bytes = table.Column<long>(type: "bigint", nullable: false),
                    mime_type = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                    width_px = table.Column<int>(type: "integer", nullable: true),
                    height_px = table.Column<int>(type: "integer", nullable: true),
                    captured_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    latitude = table.Column<double>(type: "double precision", nullable: true),
                    longitude = table.Column<double>(type: "double precision", nullable: true),
                    altitude_meters = table.Column<double>(type: "double precision", nullable: true),
                    heading_degrees = table.Column<double>(type: "double precision", nullable: true),
                    camera_angle_degrees = table.Column<double>(type: "double precision", nullable: true),
                    ai_analyzed = table.Column<bool>(type: "boolean", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_mission_images", x => x.id);
                    table.CheckConstraint("ck_mission_images_values", "file_size_bytes >= 0 AND (width_px IS NULL OR width_px > 0) AND (height_px IS NULL OR height_px > 0) AND ((latitude IS NULL AND longitude IS NULL) OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))");
                    table.ForeignKey(
                        name: "fk_mission_images_drone_missions_drone_mission_id",
                        column: x => x.drone_mission_id,
                        principalTable: "drone_missions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "mission_telemetry",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    drone_mission_id = table.Column<Guid>(type: "uuid", nullable: false),
                    sequence_number = table.Column<long>(type: "bigint", nullable: false),
                    latitude = table.Column<double>(type: "double precision", nullable: false),
                    longitude = table.Column<double>(type: "double precision", nullable: false),
                    altitude_meters = table.Column<double>(type: "double precision", nullable: false),
                    ground_speed_mps = table.Column<double>(type: "double precision", nullable: true),
                    battery_percent = table.Column<int>(type: "integer", nullable: false),
                    signal_strength_percent = table.Column<int>(type: "integer", nullable: true),
                    heading_degrees = table.Column<double>(type: "double precision", nullable: true),
                    recorded_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    server_received_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_mission_telemetry", x => x.id);
                    table.CheckConstraint("ck_mission_telemetry_values", "sequence_number >= 0 AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND altitude_meters >= 0 AND battery_percent BETWEEN 0 AND 100 AND (ground_speed_mps IS NULL OR ground_speed_mps >= 0) AND (signal_strength_percent IS NULL OR signal_strength_percent BETWEEN 0 AND 100) AND (heading_degrees IS NULL OR heading_degrees BETWEEN 0 AND 360)");
                    table.ForeignKey(
                        name: "fk_mission_telemetry_drone_missions_drone_mission_id",
                        column: x => x.drone_mission_id,
                        principalTable: "drone_missions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "report_evidence",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    report_id = table.Column<Guid>(type: "uuid", nullable: false),
                    minio_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: false),
                    thumbnail_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    file_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    file_size_bytes = table.Column<long>(type: "bigint", nullable: false),
                    mime_type = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                    caption = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    uploaded_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    uploaded_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    is_deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false),
                    deleted_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    deleted_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_report_evidence", x => x.id);
                    table.CheckConstraint("ck_report_evidence_size", "file_size_bytes >= 0");
                    table.ForeignKey(
                        name: "fk_report_evidence_inspection_reports_report_id",
                        column: x => x.report_id,
                        principalTable: "inspection_reports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_report_evidence_users_uploaded_by_user_id",
                        column: x => x.uploaded_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "report_findings",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    report_id = table.Column<Guid>(type: "uuid", nullable: false),
                    description = table.Column<string>(type: "text", nullable: false),
                    severity = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    location_note = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    latitude = table.Column<double>(type: "double precision", nullable: true),
                    longitude = table.Column<double>(type: "double precision", nullable: true),
                    image_id = table.Column<Guid>(type: "uuid", nullable: true),
                    bounding_box_json = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    confidence_score = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_report_findings", x => x.id);
                    table.CheckConstraint("ck_report_findings_confidence", "confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1");
                    table.ForeignKey(
                        name: "fk_report_findings_inspection_reports_report_id",
                        column: x => x.report_id,
                        principalTable: "inspection_reports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_report_findings_mission_images_image_id",
                        column: x => x.image_id,
                        principalTable: "mission_images",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "defects",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    finding_id = table.Column<Guid>(type: "uuid", nullable: true),
                    report_id = table.Column<Guid>(type: "uuid", nullable: false),
                    asset_id = table.Column<Guid>(type: "uuid", nullable: false),
                    defect_number = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: true),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: false),
                    severity = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    category = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    repair_recommendation = table.Column<string>(type: "text", nullable: true),
                    repair_priority = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    estimated_repair_cost = table.Column<decimal>(type: "numeric(15,2)", precision: 15, scale: 2, nullable: true),
                    estimated_repair_hours = table.Column<int>(type: "integer", nullable: true),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    detected_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    confirmed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    confirmed_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    resolved_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    resolved_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    resolution_notes = table.Column<string>(type: "text", nullable: true),
                    closed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_defects", x => x.id);
                    table.CheckConstraint("ck_defects_values", "(estimated_repair_cost IS NULL OR estimated_repair_cost >= 0) AND (estimated_repair_hours IS NULL OR estimated_repair_hours >= 0)");
                    table.ForeignKey(
                        name: "fk_defects_assets_asset_id",
                        column: x => x.asset_id,
                        principalTable: "assets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_defects_inspection_reports_report_id",
                        column: x => x.report_id,
                        principalTable: "inspection_reports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_defects_report_findings_finding_id",
                        column: x => x.finding_id,
                        principalTable: "report_findings",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_defects_users_confirmed_by_user_id",
                        column: x => x.confirmed_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_defects_users_resolved_by_user_id",
                        column: x => x.resolved_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "ai_analysis_jobs",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    mission_image_id = table.Column<Guid>(type: "uuid", nullable: true),
                    defect_id = table.Column<Guid>(type: "uuid", nullable: true),
                    report_id = table.Column<Guid>(type: "uuid", nullable: true),
                    job_type = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    priority = table.Column<int>(type: "integer", nullable: false),
                    input_payload = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    result = table.Column<JsonDocument>(type: "jsonb", nullable: true),
                    confidence = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    model_name = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: true),
                    model_version = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    prompt_tokens = table.Column<int>(type: "integer", nullable: true),
                    completion_tokens = table.Column<int>(type: "integer", nullable: true),
                    total_cost_usd = table.Column<decimal>(type: "numeric(10,6)", precision: 10, scale: 6, nullable: true),
                    latency_ms = table.Column<int>(type: "integer", nullable: true),
                    error_code = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    error_message = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    requested_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    queued_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    started_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    completed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    retry_count = table.Column<int>(type: "integer", nullable: false),
                    max_retries = table.Column<int>(type: "integer", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_ai_analysis_jobs", x => x.id);
                    table.CheckConstraint("ck_ai_jobs_confidence", "confidence IS NULL OR confidence BETWEEN 0 AND 1");
                    table.CheckConstraint("ck_ai_jobs_one_target", "num_nonnulls(mission_image_id, defect_id, report_id) = 1");
                    table.CheckConstraint("ck_ai_jobs_priority", "priority BETWEEN 1 AND 10");
                    table.CheckConstraint("ck_ai_jobs_retries", "retry_count >= 0 AND max_retries >= 0 AND retry_count <= max_retries");
                    table.ForeignKey(
                        name: "fk_ai_analysis_jobs_defects_defect_id",
                        column: x => x.defect_id,
                        principalTable: "defects",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_ai_analysis_jobs_inspection_reports_report_id",
                        column: x => x.report_id,
                        principalTable: "inspection_reports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_ai_analysis_jobs_mission_images_mission_image_id",
                        column: x => x.mission_image_id,
                        principalTable: "mission_images",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_ai_analysis_jobs_users_requested_by_user_id",
                        column: x => x.requested_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "defect_evidence",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    defect_id = table.Column<Guid>(type: "uuid", nullable: false),
                    minio_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: false),
                    thumbnail_object_key = table.Column<string>(type: "character varying(1024)", maxLength: 1024, nullable: true),
                    file_type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    file_size_bytes = table.Column<long>(type: "bigint", nullable: false),
                    mime_type = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false),
                    caption = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    uploaded_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    uploaded_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_defect_evidence", x => x.id);
                    table.CheckConstraint("ck_defect_evidence_size", "file_size_bytes >= 0");
                    table.ForeignKey(
                        name: "fk_defect_evidence_defects_defect_id",
                        column: x => x.defect_id,
                        principalTable: "defects",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_defect_evidence_users_uploaded_by_user_id",
                        column: x => x.uploaded_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "knowledge_cases",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    defect_id = table.Column<Guid>(type: "uuid", nullable: true),
                    report_id = table.Column<Guid>(type: "uuid", nullable: true),
                    title = table.Column<string>(type: "character varying(300)", maxLength: 300, nullable: false),
                    content = table.Column<string>(type: "text", nullable: false),
                    summary = table.Column<string>(type: "text", nullable: true),
                    case_type = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    tags = table.Column<string[]>(type: "text[]", nullable: true),
                    language = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false),
                    source = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    is_published = table.Column<bool>(type: "boolean", nullable: false),
                    published_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    usage_count = table.Column<int>(type: "integer", nullable: false),
                    last_used_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_knowledge_cases", x => x.id);
                    table.ForeignKey(
                        name: "fk_knowledge_cases_defects_defect_id",
                        column: x => x.defect_id,
                        principalTable: "defects",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_knowledge_cases_inspection_reports_report_id",
                        column: x => x.report_id,
                        principalTable: "inspection_reports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "maintenance_tickets",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    organization_id = table.Column<Guid>(type: "uuid", nullable: false),
                    defect_id = table.Column<Guid>(type: "uuid", nullable: true),
                    request_id = table.Column<Guid>(type: "uuid", nullable: true),
                    assigned_to_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    assigned_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    assigned_by_user_id = table.Column<Guid>(type: "uuid", nullable: true),
                    created_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    ticket_number = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: true),
                    title = table.Column<string>(type: "character varying(250)", maxLength: 250, nullable: false),
                    description = table.Column<string>(type: "text", nullable: false),
                    priority = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    due_date = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    started_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    resolved_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    closed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    resolution_notes = table.Column<string>(type: "text", nullable: true),
                    estimated_cost = table.Column<decimal>(type: "numeric(15,2)", precision: 15, scale: 2, nullable: true),
                    actual_cost = table.Column<decimal>(type: "numeric(15,2)", precision: 15, scale: 2, nullable: true),
                    created_by = table.Column<Guid>(type: "uuid", nullable: true),
                    updated_by = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_maintenance_tickets", x => x.id);
                    table.CheckConstraint("ck_maintenance_tickets_costs", "(estimated_cost IS NULL OR estimated_cost >= 0) AND (actual_cost IS NULL OR actual_cost >= 0)");
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_defects_defect_id",
                        column: x => x.defect_id,
                        principalTable: "defects",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_inspection_requests_request_id",
                        column: x => x.request_id,
                        principalTable: "inspection_requests",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_organizations_organization_id",
                        column: x => x.organization_id,
                        principalTable: "organizations",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_users_assigned_by_user_id",
                        column: x => x.assigned_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_users_assigned_to_user_id",
                        column: x => x.assigned_to_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_maintenance_tickets_users_created_by_user_id",
                        column: x => x.created_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "knowledge_case_embeddings",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    knowledge_case_id = table.Column<Guid>(type: "uuid", nullable: false),
                    embedding = table.Column<Vector>(type: "vector(1536)", nullable: false),
                    model_name = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    model_version = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    embedded_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_knowledge_case_embeddings", x => x.id);
                    table.ForeignKey(
                        name: "fk_knowledge_case_embeddings_knowledge_cases_knowledge_case_id",
                        column: x => x.knowledge_case_id,
                        principalTable: "knowledge_cases",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "ticket_history",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    ticket_id = table.Column<Guid>(type: "uuid", nullable: false),
                    from_status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: true),
                    to_status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    changed_by_user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    changed_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    comment = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    time_spent_minutes = table.Column<int>(type: "integer", nullable: true),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_ticket_history", x => x.id);
                    table.CheckConstraint("ck_ticket_history_time", "time_spent_minutes IS NULL OR time_spent_minutes >= 0");
                    table.ForeignKey(
                        name: "fk_ticket_history_maintenance_tickets_ticket_id",
                        column: x => x.ticket_id,
                        principalTable: "maintenance_tickets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_ticket_history_users_changed_by_user_id",
                        column: x => x.changed_by_user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "ix_ai_analysis_jobs_defect_id",
                table: "ai_analysis_jobs",
                column: "defect_id");

            migrationBuilder.CreateIndex(
                name: "ix_ai_analysis_jobs_mission_image_id",
                table: "ai_analysis_jobs",
                column: "mission_image_id");

            migrationBuilder.CreateIndex(
                name: "ix_ai_analysis_jobs_report_id",
                table: "ai_analysis_jobs",
                column: "report_id");

            migrationBuilder.CreateIndex(
                name: "ix_ai_analysis_jobs_requested_by_user_id",
                table: "ai_analysis_jobs",
                column: "requested_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_ai_analysis_jobs_status_priority_queued_at",
                table: "ai_analysis_jobs",
                columns: new[] { "status", "priority", "queued_at" });

            migrationBuilder.CreateIndex(
                name: "ix_asset_categories_is_deleted",
                table: "asset_categories",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_asset_categories_name",
                table: "asset_categories",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_asset_categories_parent_id",
                table: "asset_categories",
                column: "parent_id");

            migrationBuilder.CreateIndex(
                name: "ix_asset_documents_asset_id",
                table: "asset_documents",
                column: "asset_id");

            migrationBuilder.CreateIndex(
                name: "ix_asset_documents_file_key",
                table: "asset_documents",
                column: "file_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_asset_documents_is_deleted",
                table: "asset_documents",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_asset_documents_uploaded_by",
                table: "asset_documents",
                column: "uploaded_by");

            migrationBuilder.CreateIndex(
                name: "ix_asset_lifecycle_logs_asset_id_changed_at",
                table: "asset_lifecycle_logs",
                columns: new[] { "asset_id", "changed_at" });

            migrationBuilder.CreateIndex(
                name: "ix_asset_lifecycle_logs_changed_by",
                table: "asset_lifecycle_logs",
                column: "changed_by");

            migrationBuilder.CreateIndex(
                name: "ix_assets_category_id",
                table: "assets",
                column: "category_id");

            migrationBuilder.CreateIndex(
                name: "ix_assets_is_deleted",
                table: "assets",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_assets_latitude_longitude",
                table: "assets",
                columns: new[] { "latitude", "longitude" });

            migrationBuilder.CreateIndex(
                name: "ix_assets_organization_id_normalized_code",
                table: "assets",
                columns: new[] { "organization_id", "normalized_code" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_assets_organization_id_status",
                table: "assets",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_audit_logs_category_occurred_at",
                table: "audit_logs",
                columns: new[] { "category", "occurred_at" });

            migrationBuilder.CreateIndex(
                name: "ix_audit_logs_occurred_at",
                table: "audit_logs",
                column: "occurred_at");

            migrationBuilder.CreateIndex(
                name: "ix_audit_logs_user_id",
                table: "audit_logs",
                column: "user_id");

            migrationBuilder.CreateIndex(
                name: "ix_defect_evidence_defect_id",
                table: "defect_evidence",
                column: "defect_id");

            migrationBuilder.CreateIndex(
                name: "ix_defect_evidence_minio_object_key",
                table: "defect_evidence",
                column: "minio_object_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_defect_evidence_uploaded_by_user_id",
                table: "defect_evidence",
                column: "uploaded_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_defects_asset_id_detected_at",
                table: "defects",
                columns: new[] { "asset_id", "detected_at" });

            migrationBuilder.CreateIndex(
                name: "ix_defects_confirmed_by_user_id",
                table: "defects",
                column: "confirmed_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_defects_defect_number",
                table: "defects",
                column: "defect_number",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_defects_finding_id",
                table: "defects",
                column: "finding_id");

            migrationBuilder.CreateIndex(
                name: "ix_defects_organization_id_status",
                table: "defects",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_defects_report_id",
                table: "defects",
                column: "report_id");

            migrationBuilder.CreateIndex(
                name: "ix_defects_resolved_by_user_id",
                table: "defects",
                column: "resolved_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_drone_missions_cancelled_by_user_id",
                table: "drone_missions",
                column: "cancelled_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_drone_missions_inspection_request_id",
                table: "drone_missions",
                column: "inspection_request_id");

            migrationBuilder.CreateIndex(
                name: "ix_drone_missions_launched_by_user_id",
                table: "drone_missions",
                column: "launched_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_drone_missions_organization_id_status",
                table: "drone_missions",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_drone_missions_smart_drone_hub_mission_id",
                table: "drone_missions",
                column: "smart_drone_hub_mission_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_created_by_user_id",
                table: "inspection_calendar_events",
                column: "created_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_event_date",
                table: "inspection_calendar_events",
                column: "event_date");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_plan_id",
                table: "inspection_calendar_events",
                column: "plan_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_recurrence_parent_id",
                table: "inspection_calendar_events",
                column: "recurrence_parent_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_request_id",
                table: "inspection_calendar_events",
                column: "request_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_calendar_events_schedule_id",
                table: "inspection_calendar_events",
                column: "schedule_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_plans_activated_by_user_id",
                table: "inspection_plans",
                column: "activated_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_plans_created_by_user_id",
                table: "inspection_plans",
                column: "created_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_plans_is_deleted",
                table: "inspection_plans",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_plans_next_run_date",
                table: "inspection_plans",
                column: "next_run_date");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_plans_organization_id_status",
                table: "inspection_plans",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_inspection_request_id",
                table: "inspection_reports",
                column: "inspection_request_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_inspector_id",
                table: "inspection_reports",
                column: "inspector_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_is_deleted",
                table: "inspection_reports",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_mission_id",
                table: "inspection_reports",
                column: "mission_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_organization_id_status",
                table: "inspection_reports",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_rejected_by_user_id",
                table: "inspection_reports",
                column: "rejected_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_report_number",
                table: "inspection_reports",
                column: "report_number",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_inspection_reports_reviewed_by_user_id",
                table: "inspection_reports",
                column: "reviewed_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_asset_id",
                table: "inspection_requests",
                column: "asset_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_decided_by_user_id",
                table: "inspection_requests",
                column: "decided_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_inspector_id",
                table: "inspection_requests",
                column: "inspector_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_mission_creation_key",
                table: "inspection_requests",
                column: "mission_creation_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_organization_id_status",
                table: "inspection_requests",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_plan_id",
                table: "inspection_requests",
                column: "plan_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_requests_requested_by_user_id",
                table: "inspection_requests",
                column: "requested_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_schedules_asset_id_scheduled_date",
                table: "inspection_schedules",
                columns: new[] { "asset_id", "scheduled_date" });

            migrationBuilder.CreateIndex(
                name: "ix_inspection_schedules_assigned_by_user_id",
                table: "inspection_schedules",
                column: "assigned_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_schedules_inspector_id_scheduled_date",
                table: "inspection_schedules",
                columns: new[] { "inspector_id", "scheduled_date" });

            migrationBuilder.CreateIndex(
                name: "ix_inspection_schedules_plan_id",
                table: "inspection_schedules",
                column: "plan_id");

            migrationBuilder.CreateIndex(
                name: "ix_inspection_schedules_rescheduled_from_id",
                table: "inspection_schedules",
                column: "rescheduled_from_id");

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_case_embeddings_embedding",
                table: "knowledge_case_embeddings",
                column: "embedding")
                .Annotation("Npgsql:IndexMethod", "hnsw")
                .Annotation("Npgsql:IndexOperators", new[] { "vector_cosine_ops" });

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_case_embeddings_knowledge_case_id",
                table: "knowledge_case_embeddings",
                column: "knowledge_case_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_cases_defect_id",
                table: "knowledge_cases",
                column: "defect_id");

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_cases_is_published_case_type",
                table: "knowledge_cases",
                columns: new[] { "is_published", "case_type" });

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_cases_report_id",
                table: "knowledge_cases",
                column: "report_id");

            migrationBuilder.CreateIndex(
                name: "ix_knowledge_cases_tags",
                table: "knowledge_cases",
                column: "tags")
                .Annotation("Npgsql:IndexMethod", "gin");

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_assigned_by_user_id",
                table: "maintenance_tickets",
                column: "assigned_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_assigned_to_user_id_status",
                table: "maintenance_tickets",
                columns: new[] { "assigned_to_user_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_created_by_user_id",
                table: "maintenance_tickets",
                column: "created_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_defect_id",
                table: "maintenance_tickets",
                column: "defect_id");

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_organization_id_status",
                table: "maintenance_tickets",
                columns: new[] { "organization_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_request_id",
                table: "maintenance_tickets",
                column: "request_id");

            migrationBuilder.CreateIndex(
                name: "ix_maintenance_tickets_ticket_number",
                table: "maintenance_tickets",
                column: "ticket_number",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_mission_flight_logs_drone_mission_id_logged_at",
                table: "mission_flight_logs",
                columns: new[] { "drone_mission_id", "logged_at" });

            migrationBuilder.CreateIndex(
                name: "ix_mission_flight_logs_drone_mission_id_sequence_number",
                table: "mission_flight_logs",
                columns: new[] { "drone_mission_id", "sequence_number" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_mission_images_drone_mission_id_captured_at",
                table: "mission_images",
                columns: new[] { "drone_mission_id", "captured_at" });

            migrationBuilder.CreateIndex(
                name: "ix_mission_images_minio_object_key",
                table: "mission_images",
                column: "minio_object_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_mission_telemetry_drone_mission_id_recorded_at",
                table: "mission_telemetry",
                columns: new[] { "drone_mission_id", "recorded_at" });

            migrationBuilder.CreateIndex(
                name: "ix_mission_telemetry_drone_mission_id_sequence_number",
                table: "mission_telemetry",
                columns: new[] { "drone_mission_id", "sequence_number" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_notifications_idempotency_key",
                table: "notifications",
                column: "idempotency_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_notifications_user_id_is_read",
                table: "notifications",
                columns: new[] { "user_id", "is_read" });

            migrationBuilder.CreateIndex(
                name: "ix_organizations_code",
                table: "organizations",
                column: "code",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_organizations_is_deleted",
                table: "organizations",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_organizations_name",
                table: "organizations",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_plan_assets_asset_id",
                table: "plan_assets",
                column: "asset_id");

            migrationBuilder.CreateIndex(
                name: "ix_refresh_tokens_jwt_id",
                table: "refresh_tokens",
                column: "jwt_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_refresh_tokens_replaced_by_token_id",
                table: "refresh_tokens",
                column: "replaced_by_token_id");

            migrationBuilder.CreateIndex(
                name: "ix_refresh_tokens_token_hash",
                table: "refresh_tokens",
                column: "token_hash",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_refresh_tokens_user_id",
                table: "refresh_tokens",
                column: "user_id");

            migrationBuilder.CreateIndex(
                name: "ix_report_evidence_is_deleted",
                table: "report_evidence",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_report_evidence_minio_object_key",
                table: "report_evidence",
                column: "minio_object_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_report_evidence_report_id",
                table: "report_evidence",
                column: "report_id");

            migrationBuilder.CreateIndex(
                name: "ix_report_evidence_uploaded_by_user_id",
                table: "report_evidence",
                column: "uploaded_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_report_findings_image_id",
                table: "report_findings",
                column: "image_id");

            migrationBuilder.CreateIndex(
                name: "ix_report_findings_report_id_severity",
                table: "report_findings",
                columns: new[] { "report_id", "severity" });

            migrationBuilder.CreateIndex(
                name: "ix_system_settings_key",
                table: "system_settings",
                column: "key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_system_settings_updated_by",
                table: "system_settings",
                column: "updated_by");

            migrationBuilder.CreateIndex(
                name: "ix_system_settings_version",
                table: "system_settings",
                column: "version");

            migrationBuilder.CreateIndex(
                name: "ix_ticket_history_changed_by_user_id",
                table: "ticket_history",
                column: "changed_by_user_id");

            migrationBuilder.CreateIndex(
                name: "ix_ticket_history_ticket_id_changed_at",
                table: "ticket_history",
                columns: new[] { "ticket_id", "changed_at" });

            migrationBuilder.CreateIndex(
                name: "ix_users_is_deleted",
                table: "users",
                column: "is_deleted");

            migrationBuilder.CreateIndex(
                name: "ix_users_normalized_email",
                table: "users",
                column: "normalized_email",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_users_normalized_username",
                table: "users",
                column: "normalized_username",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_users_organization_id",
                table: "users",
                column: "organization_id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "ai_analysis_jobs");

            migrationBuilder.DropTable(
                name: "asset_documents");

            migrationBuilder.DropTable(
                name: "asset_lifecycle_logs");

            migrationBuilder.DropTable(
                name: "audit_logs");

            migrationBuilder.DropTable(
                name: "defect_evidence");

            migrationBuilder.DropTable(
                name: "inspection_calendar_events");

            migrationBuilder.DropTable(
                name: "knowledge_case_embeddings");

            migrationBuilder.DropTable(
                name: "mission_flight_logs");

            migrationBuilder.DropTable(
                name: "mission_telemetry");

            migrationBuilder.DropTable(
                name: "notifications");

            migrationBuilder.DropTable(
                name: "plan_assets");

            migrationBuilder.DropTable(
                name: "refresh_tokens");

            migrationBuilder.DropTable(
                name: "report_evidence");

            migrationBuilder.DropTable(
                name: "system_settings");

            migrationBuilder.DropTable(
                name: "ticket_history");

            migrationBuilder.DropTable(
                name: "inspection_schedules");

            migrationBuilder.DropTable(
                name: "knowledge_cases");

            migrationBuilder.DropTable(
                name: "maintenance_tickets");

            migrationBuilder.DropTable(
                name: "defects");

            migrationBuilder.DropTable(
                name: "report_findings");

            migrationBuilder.DropTable(
                name: "inspection_reports");

            migrationBuilder.DropTable(
                name: "mission_images");

            migrationBuilder.DropTable(
                name: "drone_missions");

            migrationBuilder.DropTable(
                name: "inspection_requests");

            migrationBuilder.DropTable(
                name: "assets");

            migrationBuilder.DropTable(
                name: "inspection_plans");

            migrationBuilder.DropTable(
                name: "asset_categories");

            migrationBuilder.DropTable(
                name: "users");

            migrationBuilder.DropTable(
                name: "organizations");
        }
    }
}
