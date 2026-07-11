$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$etl = Join-Path $root 'trace-fileio.etl'
$log = Join-Path $root 'trace-fileio.log'
$scan = (Get-ChildItem -LiteralPath 'C:\Users\PC\Desktop' -Recurse -File -Filter 'Scan_001.tzf' | Select-Object -First 1 -ExpandProperty FullName)
$csv = Join-Path $root 'testdata\trace-column1000.csv'
if (-not $scan) { throw 'Scan_001.tzf not found under Desktop' }

try {
    Start-Transcript -Path $log -Force
    cmd.exe /c "wpr -cancel >nul 2>nul"
    wpr -start FileIO -filemode
    & (Join-Path $PSScriptRoot 'export_oracle_column.ps1') -InputFile $scan -Column 1000 -OutputFile $csv
    wpr -stop $etl
    Write-Host "Done: $etl"
} catch {
    cmd.exe /c "wpr -cancel >nul 2>nul"
    $_ | Out-File -FilePath $log -Append -Encoding utf8
    throw
} finally {
    Stop-Transcript -ErrorAction SilentlyContinue
}
