# Preview a debloat list while preserving connectivity and unreviewed system packages.
# Add -Apply to disable eligible packages. Use compatibility.py --restore for exact rollback.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost = $env:TV_HOST,
    [switch]$Apply,
    [string]$Protect,
    [string]$ReviewedSystem,
    [string]$Output = 'backup/compatibility'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path -LiteralPath $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$compatArguments = @($List, '--output', $Output)
if ($Apply) { $compatArguments += '--apply' }
if ($Protect) { $compatArguments += @('--protect', (Resolve-Path -LiteralPath $Protect).Path) }
if ($ReviewedSystem) { $compatArguments += @('--reviewed-system', (Resolve-Path -LiteralPath $ReviewedSystem).Path) }
& $py -I -c 'import runpy,sys; sys.path.insert(0,sys.argv.pop(1)); runpy.run_module("compatibility",run_name="__main__")' $PSScriptRoot @compatArguments
if ($LASTEXITCODE -ne 0) { throw 'Compatibility scan/apply failed; inspect its report and restore that journal if needed' }
