# Builds self-contained URSC bundles for Windows:
#   dist\URSC\                       jpackage application image (URSC.exe + private Java runtime)
#   dist\URSC-windows-portable\      private runtime + jar + .cmd start scripts
#   dist\*.zip + SHA256SUMS.txt      archives to carry into the air-gapped facility
# Needs a JDK 17+ (jlink, jpackage) and Maven on the build machine only.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$root = Get-Location
$dist = Join-Path $root 'dist'
$version = ([xml](Get-Content pom.xml)).project.version
if ($env:SKIP_BUILD -ne '1') { mvn -B -q -DskipTests package; if ($LASTEXITCODE) { throw 'maven build failed' } }
$jar = Get-ChildItem target\leo-trajectory-*.jar | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
$modules = (jdeps --print-module-deps --ignore-missing-deps $jar.FullName | Select-Object -Last 1).Trim()
Write-Host "modules: $modules"
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force (Join-Path $dist 'input') | Out-Null
Copy-Item $jar.FullName (Join-Path $dist 'input\ursc.jar')
# the Orekit engine needs the JPL DE440 ephemeris; it is looked up next to the jar
New-Item -ItemType Directory -Force (Join-Path $dist 'input\data\ephemeris') | Out-Null
Copy-Item 'data\ephemeris\lnxp1990.440' (Join-Path $dist 'input\data\ephemeris\')

jlink --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $dist 'runtime')
if ($LASTEXITCODE) { jlink --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $dist 'runtime') }

# --win-console keeps a console window showing the address and log; close it to stop URSC
jpackage --type app-image --name URSC --app-version $version `
  --input (Join-Path $dist 'input') --main-jar ursc.jar --main-class org.ursc.trajectory.app.Cli `
  --runtime-image (Join-Path $dist 'runtime') --arguments gui --win-console `
  --java-options '-Dfile.encoding=UTF-8' `
  --description 'URSC LEO orbit determination and prediction (offline)' --dest $dist
if ($LASTEXITCODE) { throw 'jpackage failed' }

$p = Join-Path $dist 'URSC-windows-portable'
New-Item -ItemType Directory -Force (Join-Path $p 'lib') | Out-Null
Copy-Item -Recurse (Join-Path $dist 'runtime') (Join-Path $p 'runtime')
Copy-Item (Join-Path $dist 'input\ursc.jar') (Join-Path $p 'lib\ursc.jar')
New-Item -ItemType Directory -Force (Join-Path $p 'data\ephemeris') | Out-Null
Copy-Item 'data\ephemeris\lnxp1990.440' (Join-Path $p 'data\ephemeris\')
Set-Content (Join-Path $p 'ursc-gui.cmd') "@echo off`r`nrem Starts the URSC GUI on this computer only (127.0.0.1) and opens the browser.`r`n`"%~dp0runtime\bin\java.exe`" -Dfile.encoding=UTF-8 -jar `"%~dp0lib\ursc.jar`" gui %*`r`n"
Set-Content (Join-Path $p 'ursc.cmd') "@echo off`r`n`"%~dp0runtime\bin\java.exe`" -Dfile.encoding=UTF-8 -jar `"%~dp0lib\ursc.jar`" %*`r`n"
Copy-Item packaging\README-OFFLINE.md $p
Copy-Item packaging\README-OFFLINE.md (Join-Path $dist 'URSC')

Push-Location $dist
Compress-Archive -Path URSC -DestinationPath "URSC-$version-windows-app.zip"
Compress-Archive -Path URSC-windows-portable -DestinationPath "URSC-$version-windows-portable.zip"
Get-ChildItem *.zip | ForEach-Object { "{0}  {1}" -f (Get-FileHash $_ -Algorithm SHA256).Hash.ToLower(), $_.Name } | Set-Content SHA256SUMS.txt
Remove-Item -Recurse -Force input, runtime
Pop-Location
Get-ChildItem $dist
