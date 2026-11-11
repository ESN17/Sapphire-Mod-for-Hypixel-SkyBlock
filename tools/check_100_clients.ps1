param([string[]]$Targets = @('26.1.2', '26.2', '26.3'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$candidateRoot = Join-Path $workspace 'build/release-1.0.0-candidates'
foreach ($mc in $Targets) {
    if ($mc -notin @('26.1.2', '26.2', '26.3')) { throw 'Unsupported target' }
    $stage = Join-Path $candidateRoot "mc$mc"
    $gameDir = Join-Path $candidateRoot "ui-mc$mc"
    Push-Location $stage
    try {
        foreach ($kind in @('smoke', 'ui')) {
            $flag = if ($kind -eq 'ui') { '-PsapphireApiSmoke' } else { '-PsapphireSmoke' }
            $marker = if ($kind -eq 'ui') { 'SAPPHIRE_API_UI_SMOKE_OK' } else { 'SAPPHIRE_SMOKE_OK' }
            $log = Join-Path $candidateRoot "mc$mc-$kind.log"
            Write-Output "Starting Minecraft $mc $kind check"
            & .\gradlew.bat runClient $flag "-PsapphireSmokeDir=$gameDir" --offline --max-workers=1 --console=plain *> $log
            if ($LASTEXITCODE -ne 0) { throw "Minecraft $mc $kind failed; inspect $log" }
            if (-not (Select-String -LiteralPath $log -Pattern $marker -Quiet)) { throw "Missing $marker; inspect $log" }
            Write-Output "Minecraft $mc $kind passed"
        }
    } finally { Pop-Location }
}
