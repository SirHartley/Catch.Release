param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome with a Java 17 JDK directory.' }
$repoRoot = (Resolve-Path "$PSScriptRoot/..").Path
$output = Join-Path $repoRoot ('out/fish-tool-tests-' + [guid]::NewGuid().ToString('N'))
Push-Location -LiteralPath $repoRoot
try {
    New-Item -ItemType Directory -Path $output | Out-Null
    & "$JavaHome/bin/javac.exe" --release 17 -encoding UTF-8 -sourcepath jars/src -d $output `
        jars/src/catchrelease/tools/FishDifficultyTuner.java `
        jars/src/catchrelease/tools/FishFacingPicker.java `
        tests/catchrelease/tools/FishTunerChecks.java
    if ($LASTEXITCODE -ne 0) { throw 'Fish tool compilation failed.' }
    & "$JavaHome/bin/java.exe" '-Djava.awt.headless=true' -cp $output catchrelease.tools.FishTunerChecks
    if ($LASTEXITCODE -ne 0) { throw 'Fish tool checks failed.' }
} finally {
    Pop-Location
}
