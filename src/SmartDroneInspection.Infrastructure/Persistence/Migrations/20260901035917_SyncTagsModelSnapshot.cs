using System.Collections.Generic;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SmartDroneInspection.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class SyncTagsModelSnapshot : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterColumn<List<string>>(
                name: "tags",
                table: "knowledge_cases",
                type: "text[]",
                nullable: false,
                defaultValue: new List<string>(),
                oldClrType: typeof(string[]),
                oldType: "text[]",
                oldNullable: true);

            migrationBuilder.AlterColumn<List<string>>(
                name: "tags",
                table: "assets",
                type: "text[]",
                nullable: false,
                defaultValue: new List<string>(),
                oldClrType: typeof(string[]),
                oldType: "text[]",
                oldNullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AlterColumn<string[]>(
                name: "tags",
                table: "knowledge_cases",
                type: "text[]",
                nullable: true,
                oldClrType: typeof(List<string>),
                oldType: "text[]");

            migrationBuilder.AlterColumn<string[]>(
                name: "tags",
                table: "assets",
                type: "text[]",
                nullable: true,
                oldClrType: typeof(List<string>),
                oldType: "text[]");
        }
    }
}
