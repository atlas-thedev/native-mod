# Prints the next version and (unless -DryRun or Part=none) writes it to gradle.properties.
#   powershell -File scripts\bump-version.ps1 patch|minor|major|none [-DryRun]
param(
    [string]$Part = 'patch',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$file = Join-Path $PSScriptRoot '..\gradle.properties'
$text = [IO.File]::ReadAllText($file)
if ($text -notmatch '(?m)^version=(\d+)\.(\d+)\.(\d+)[^\r\n]*') {
    Write-Error 'No version=x.y.z line found in gradle.properties'
    exit 1
}
$major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]
switch ($Part) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    'patch' { $patch++ }
    'none'  { }
    default { Write-Error "Unknown bump '$Part' (use patch, minor, major or none)"; exit 1 }
}
$next = "$major.$minor.$patch"
if (-not $DryRun -and $Part -ne 'none') {
    $text = [regex]::Replace($text, '(?m)^version=\d+\.\d+\.\d+', "version=$next")
    [IO.File]::WriteAllText($file, $text)
}
Write-Output $next
