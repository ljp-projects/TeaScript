#! /usr/local/bin/pwsh
# NOTE: Installing a legacy branch is not supported, as earlier installers require Bash

Param(
    [Parameter(HelpMessage = "When given, use the JAR associated with this tag.")]
    [string]$tag,
    [Parameter(HelpMessage = "When given, use the installer associated with this branch.")]
    [string]$legacy_branch,
    [Parameter(HelpMessage = "When given, use the given JAR and don't install anything.")]
    [string]$from_jar
)

$tea_version = $(Invoke-RestMethod -Uri "https://api.github.com/repos/ljp-projects/teascript/tags")[0].name
$message = "teaScript $tea_version has been installed successfully"
$length = $message.Length
$border = "-" * $length

if ( !(Test-Path -Path C:\Scripts) ) {
    mkdir C:\Scripts
}

Set-Location C:\Scripts

if ( $tag -ne $null ) {
    Invoke-RestMethod -OutFile "TeaScript.jar" -Uri "https://github.com/ljp-projects/TeaScript/releases/download/$tag/TeaScript.jar"

    $message = "TeaScript $tag has been installed successfuly!"
    $length = $message.Length
    $border = $("-" * $length)
} elseif ( $from_jar -eq $null ) {
    Invoke-RestMethod -OutFile "TeaScript.jar" -Uri "https://github.com/ljp-projects/TeaScript/releases/download/$tag/TeaScript.jar"
} else {
    Move-Item "$from_jar" "C:\Scripts"
}

if ( !(Test-Path -Path C:\Scripts\tea.bat) ) {
    New-Item -ItemType File -Path C:\Scripts\tea.bat
}

Set-Content -Path C:\Scripts\tea.bat -Value "java -jar C:\Scripts\TeaScript.jar %*"

Write-Host "$border"
Write-Host "$message"
Write-Host "$border"