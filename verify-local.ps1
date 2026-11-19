#requires -Version 5.1
<#
.SYNOPSIS
  Runs the backend quality gate locally when GitHub-hosted Actions are unavailable.

.DESCRIPTION
  Checks the runtime prerequisites for `mvnw verify` (Docker daemon and ffmpeg),
  then runs Spotless, all tests, the JaCoCo 80% line/instruction gate, and the
  Spring Modulith boundary check.

  Run this from the backend repository root:
    powershell -ExecutionPolicy Bypass -File .\verify-local.ps1
#>
[CmdletBinding()]
param(
  [switch]$SkipDockerCheck,
  [switch]$SkipFfmpegCheck
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path '.\mvnw.cmd')) {
  throw 'Run this script from the backend repository root (mvnw.cmd not found).'
}

if (-not $SkipDockerCheck) {
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is not installed. Install Docker Desktop before running the backend gate.'
  }
  cmd /c "docker info >nul 2>&1"
  if ($LASTEXITCODE -ne 0) {
    throw 'Docker daemon is not running. Start Docker Desktop, then rerun this script.'
  }
}

if (-not $SkipFfmpegCheck) {
  if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw @'
ffmpeg is not on PATH. It is required by FfmpegVideoTranscoderTest and
VideoEvidenceTraceAndScanIntegrationTest. Install it (for example
`winget install Gyan.FFmpeg`) or prepend a portable ffmpeg bin directory to PATH,
then rerun this script.
'@
  }
}

Write-Host 'Running backend quality gate: spotless + tests + JaCoCo + Modulith ...' -ForegroundColor Cyan
& .\mvnw.cmd verify
exit $LASTEXITCODE
