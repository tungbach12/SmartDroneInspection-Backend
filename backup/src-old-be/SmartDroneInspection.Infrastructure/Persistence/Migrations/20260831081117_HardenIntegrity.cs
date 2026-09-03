using System.Text.Json;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SmartDroneInspection.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class HardenIntegrity : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "bounding_box_json",
                table: "report_findings");

            migrationBuilder.AddColumn<JsonDocument>(
                name: "bounding_box",
                table: "report_findings",
                type: "jsonb",
                nullable: true);

            migrationBuilder.AddCheckConstraint(
                name: "ck_inspection_calendar_events_date_range",
                table: "inspection_calendar_events",
                sql: "end_date IS NULL OR end_date >= event_date");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropCheckConstraint(
                name: "ck_inspection_calendar_events_date_range",
                table: "inspection_calendar_events");

            migrationBuilder.DropColumn(
                name: "bounding_box",
                table: "report_findings");

            migrationBuilder.AddColumn<string>(
                name: "bounding_box_json",
                table: "report_findings",
                type: "character varying(500)",
                maxLength: 500,
                nullable: true);
        }
    }
}
