param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome with a Java 17 JDK directory.' }
$repoRoot = (Resolve-Path "$PSScriptRoot/..").Path
$output = Join-Path $repoRoot ('out/fish-parity-' + [guid]::NewGuid().ToString('N'))
Push-Location -LiteralPath $repoRoot
try {
    New-Item -ItemType Directory -Path $output | Out-Null
    # Compile actual runtime physics, but no campaign engine or treasure RNG.
    & "$JavaHome/bin/javac.exe" --release 17 -encoding UTF-8 -cp $output `
        -sourcepath 'tests/fishing-parity/stubs;dev-tools/src' -d $output `
        jars/src/catchrelease/campaign/fish/minigame/FishingMinigame.java `
        jars/src/catchrelease/campaign/fish/constants/FishConstants.java `
        jars/src/catchrelease/campaign/fish/data/FishMotion.java `
        jars/src/catchrelease/campaign/fish/tackle/Tackle.java `
        tests/fishing-parity/catchrelease/tools/FishingParityChecks.java
    if ($LASTEXITCODE -ne 0) { throw 'Parity fixture compilation failed.' }
    & "$JavaHome/bin/java.exe" '-Djava.awt.headless=true' -cp $output catchrelease.tools.FishingParityChecks
    if ($LASTEXITCODE -ne 0) { throw 'Game and simulator differ.' }
} finally {
    Pop-Location
}
