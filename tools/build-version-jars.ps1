$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$backupDir = Join-Path $root "jar-backups"
$distDir = Join-Path $root "dist"
$modsDir = Join-Path $env:APPDATA ".minecraft\mods"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

if (Test-Path -LiteralPath $modsDir) {
    Get-ChildItem -LiteralPath $modsDir -Filter "seed-xray-*.jar" -File | ForEach-Object {
        $backupName = "{0}-installed-backup-{1}{2}" -f $_.BaseName, $stamp, $_.Extension
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $backupDir $backupName) -Force
        Write-Host "Backed up installed jar: $($_.Name) -> jar-backups\$backupName"
    }
}

$variants = @(
    @{
        Minecraft = "1.21"
        Yarn = "1.21+build.9"
        FabricApi = "0.102.0+1.21"
    },
    @{
        Minecraft = "1.21.11"
        Yarn = "1.21.11+build.5"
        FabricApi = "0.141.4+1.21.11"
    },
    @{
        Minecraft = "1.21.10"
        Yarn = "1.21.10+build.3"
        FabricApi = "0.138.4+1.21.10"
    }
)

Push-Location $root
try {
    foreach ($variant in $variants) {
        $minecraft = $variant.Minecraft
        $yarn = $variant.Yarn
        $fabricApi = $variant.FabricApi
        Write-Host "Building Seed X-Ray for Minecraft $minecraft..."
        & .\gradlew.bat clean build "-Pminecraft_version=$minecraft" "-Pyarn_mappings=$yarn" "-Pfabric_version=$fabricApi"
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed for Minecraft $minecraft"
        }

        $builtJar = Join-Path $root "build\libs\seed-xray-0.1.12.jar"
        if (!(Test-Path -LiteralPath $builtJar)) {
            throw "Expected jar was not produced: $builtJar"
        }

        $outJar = Join-Path $distDir "seed-xray-0.1.12+mc$minecraft.jar"
        if (Test-Path -LiteralPath $outJar) {
            $backupName = "{0}-dist-backup-{1}{2}" -f [System.IO.Path]::GetFileNameWithoutExtension($outJar), $stamp, [System.IO.Path]::GetExtension($outJar)
            Copy-Item -LiteralPath $outJar -Destination (Join-Path $backupDir $backupName) -Force
            Write-Host "Backed up existing dist jar: $(Split-Path $outJar -Leaf) -> jar-backups\$backupName"
        }
        Copy-Item -LiteralPath $builtJar -Destination $outJar -Force
        Write-Host "Wrote dist\$(Split-Path $outJar -Leaf)"
    }
}
finally {
    Pop-Location
}

Write-Host "Versioned jars are in dist\. Installed mods were only backed up, not replaced."
