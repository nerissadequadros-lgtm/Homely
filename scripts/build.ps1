$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkDir = Get-ChildItem (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1
if ($jdkDir) { $env:JAVA_HOME = $jdkDir.FullName }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-cache'
Push-Location (Join-Path $projectRoot 'android')
try {
  & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
  if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação/verificação.' }
} finally { Pop-Location }
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'artifacts') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'android/app/build/outputs/apk/debug/app-debug.apk') -Destination (Join-Path $projectRoot 'artifacts/homely-0.8.0-debug.apk')
