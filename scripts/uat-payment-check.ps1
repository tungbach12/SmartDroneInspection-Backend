<#
.SYNOPSIS
  Local/UAT pre-flight check and webhook simulator for VNPay Sandbox & Sepay.
  Reads credentials from .env in the backend root. Never commit .env or secrets.
#>
param(
  [string]$BaseUrl = "http://localhost:8080",
  [long]$OrderCode = 0,
  [long]$AmountVnd = 0,
  [ValidateSet("check", "vnpay-ipn", "vnpay-cancel", "sepay-webhook")]
  [string]$Mode = "check"
)

$ErrorActionPreference = "Stop"
$envPath = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (-not (Test-Path $envPath)) {
  Write-Error "Missing .env at $envPath"
}

$envMap = @{}
Get-Content $envPath | ForEach-Object {
  if ($_ -match '^\s*([^#=]+?)=(.*)$') {
    $envMap[$matches[1].Trim()] = $matches[2].Trim()
  }
}

function Show-Check($name, $isSecret) {
  $val = $envMap[$name]
  $status = if ([string]::IsNullOrWhiteSpace($val)) { "MISSING" } elseif ($isSecret) { "SET (len=$($val.Length))" } else { $val }
  Write-Host ("  {0,-28}: {1}" -f $name, $status)
}

Write-Host "=== Payment Gateway Env Check ==="
Show-Check "VNPAY_ENABLED" $false
Show-Check "VNPAY_PAY_URL" $false
Show-Check "VNPAY_TMN_CODE" $false
Show-Check "VNPAY_HASH_SECRET" $true
Show-Check "VNPAY_RETURN_URL" $false
Show-Check "VNPAY_FRONTEND_RETURN_URL" $false
Show-Check "SEPAY_ENABLED" $false
Show-Check "SEPAY_API_KEY" $true
Show-Check "SEPAY_ACCOUNT_NUMBER" $false
Show-Check "SEPAY_BANK_NAME" $false

if ($envMap["VNPAY_RETURN_URL"] -match "localhost|127\.0\.0\.1") {
  Write-Warning "VNPAY_RETURN_URL points to localhost. Browser return works locally, but VNPay IPN and Sepay webhooks require a public HTTPS tunnel or staging domain."
}

if ($Mode -eq "check") {
  exit 0
}

if ($OrderCode -le 0 -or $AmountVnd -le 0) {
  Write-Error "Provide -OrderCode and -AmountVnd for mode '$Mode'."
}

if ($Mode -eq "vnpay-ipn" -or $Mode -eq "vnpay-cancel") {
  $secret = $envMap["VNPAY_HASH_SECRET"]
  if ([string]::IsNullOrWhiteSpace($secret)) { Write-Error "VNPAY_HASH_SECRET is empty." }
  $respCode = if ($Mode -eq "vnpay-cancel") { "24" } else { "00" }
  $params = [ordered]@{
    vnp_Amount            = ($AmountVnd * 100).ToString()
    vnp_BankCode          = "NCB"
    vnp_ResponseCode      = $respCode
    vnp_TransactionNo     = "UAT" + (Get-Date -Format "HHmmss")
    vnp_TransactionStatus = $respCode
    vnp_TxnRef            = $OrderCode.ToString()
  }
  $sortedKeys = $params.Keys | Sort-Object
  $pairs = foreach ($k in $sortedKeys) {
    $ek = [System.Net.WebUtility]::UrlEncode($k)
    $ev = [System.Net.WebUtility]::UrlEncode($params[$k])
    "$ek=$ev"
  }
  $hashData = $pairs -join "&"
  $hmac = [System.Security.Cryptography.HMACSHA512]::new([System.Text.Encoding]::UTF8.GetBytes($secret))
  $sig = ([System.BitConverter]::ToString($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($hashData))) -replace "-", "").ToLowerInvariant()
  $url = "$BaseUrl/api/v1/webhooks/vnpay/ipn?$hashData&vnp_SecureHash=$sig"
  Write-Host "Calling VNPay IPN ($Mode)..."
  $res = Invoke-RestMethod -Method Get -Uri $url
  $res | ConvertTo-Json
}

if ($Mode -eq "sepay-webhook") {
  $apiKey = $envMap["SEPAY_API_KEY"]
  $account = $envMap["SEPAY_ACCOUNT_NUMBER"]
  if ([string]::IsNullOrWhiteSpace($apiKey)) { Write-Error "SEPAY_API_KEY is empty." }
  $body = @{
    id              = [int64](Get-Date -UFormat %s)
    gateway         = $envMap["SEPAY_BANK_NAME"]
    transactionDate = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    accountNumber   = $account
    subAccount      = ""
    code            = "UATCODE"
    content         = "SDI$OrderCode UAT payment"
    transferType    = "in"
    description     = "UAT simulated transfer"
    transferAmount  = $AmountVnd
    accumulated     = $AmountVnd
    referenceCode   = "FTUAT" + (Get-Date -Format "HHmmss")
  } | ConvertTo-Json
  Write-Host "Calling Sepay webhook..."
  $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/webhooks/sepay" `
    -Headers @{ Authorization = "Apikey $apiKey" } `
    -ContentType "application/json" `
    -Body $body
  $res | ConvertTo-Json
}
