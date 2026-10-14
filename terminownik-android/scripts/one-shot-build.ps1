# ============================================================================
#  TERMINOWNIK – build podpisanej wersji release na Windows, bez przygotowań
# ============================================================================
# Skrypt SAM instaluje JDK 21 i Android SDK (bez Android Studio), tworzy trwały
# klucz podpisywania, kompiluje, podpisuje i weryfikuje APK.
#
# UŻYCIE (PowerShell 5.1 lub nowszy, w katalogu terminownik-android):
#
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   .\scripts\one-shot-build.ps1
#
# Flagi:  -SkipTests  -Clean  -ToolsDir "D:\narzedzia"
#
# WYNIK:
#   dist\terminownik-release.apk
#   dist\SHA256SUMS.txt
#   dist\VERIFY_REPORT.md
#   dist\BUILD_FAILURE_LOG.txt   (tylko przy nieudanej kompilacji)
# ============================================================================
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$Clean,
    [string]$ToolsDir = ""
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$Start = Get-Date

if ([string]::IsNullOrWhiteSpace($ToolsDir)) {
    $ToolsDir = Join-Path $HOME '.terminownik-tools'
}
$SdkDir = Join-Path $ToolsDir 'android-sdk'
$JdkDir = Join-Path $ToolsDir 'jdk21'

function Step([string]$n, [string]$msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Info([string]$msg) { Write-Host "    $msg" }
function Fail([string]$msg) { Write-Host "`nBŁĄD: $msg" -ForegroundColor Red; exit 1 }

function Get-File([string]$Url, [string]$Out) {
    foreach ($attempt in 1..3) {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $Out -UseBasicParsing -TimeoutSec 900
            return $true
        } catch {
            Info "próba $attempt/3 nieudana: $Url"
            Start-Sleep -Seconds 3
        }
    }
    return $false
}

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

# --- 1. JDK -----------------------------------------------------------------------------------
Step '1/6' 'JDK 17-21'
$Arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
$JavacExe = Join-Path $JdkDir 'bin\javac.exe'

if (-not (Test-Path $JavacExe)) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $JdkDir = $env:JAVA_HOME
        $JavacExe = Join-Path $JdkDir 'bin\javac.exe'
        Info "używam JAVA_HOME: $JdkDir"
    } elseif (Get-Command javac -ErrorAction SilentlyContinue) {
        $JdkDir = Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source)
        Info "używam JDK z PATH: $JdkDir"
    }
}

if (-not (Test-Path $JavacExe)) {
    Info 'brak JDK – pobieram Temurin 21 (ok. 190 MB, jednorazowo)'
    $Zip = Join-Path $ToolsDir 'jdk21.zip'
    $Urls = @(
        "https://api.adoptium.net/v3/binary/latest/21/ga/windows/$Arch/jdk/hotspot/normal/eclipse",
        "https://corretto.aws/downloads/latest/amazon-corretto-21-$Arch-windows-jdk.zip"
    )
    $ok = $false
    foreach ($u in $Urls) { Info "próbuję: $u"; if (Get-File $u $Zip) { $ok = $true; break } }
    if (-not $ok) { Fail 'nie udało się pobrać JDK. Zainstaluj Temurin 21 (https://adoptium.net) i uruchom ponownie.' }
    $Tmp = Join-Path $ToolsDir 'jdk-extract'
    if (Test-Path $Tmp) { Remove-Item -Recurse -Force $Tmp }
    Expand-Archive -Path $Zip -DestinationPath $Tmp -Force
    $Found = Get-ChildItem -Path $Tmp -Recurse -Filter 'javac.exe' | Select-Object -First 1
    if (-not $Found) { Fail 'archiwum JDK nie zawiera javac.exe' }
    $Extracted = Split-Path -Parent (Split-Path -Parent $Found.FullName)
    if (Test-Path $JdkDir) { Remove-Item -Recurse -Force $JdkDir }
    Move-Item -Path $Extracted -Destination $JdkDir
    Remove-Item -Force $Zip; Remove-Item -Recurse -Force $Tmp
}

$env:JAVA_HOME = $JdkDir
$env:Path = "$JdkDir\bin;$env:Path"
Info "JAVA_HOME=$JdkDir"
Info (& "$JdkDir\bin\java.exe" -version 2>&1 | Select-Object -First 1)

# --- 2. Android SDK ----------------------------------------------------------------------------
Step '2/6' 'Android SDK'
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { $SdkDir = $env:ANDROID_HOME }
elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") { $SdkDir = "$env:LOCALAPPDATA\Android\Sdk" }
New-Item -ItemType Directory -Force -Path $SdkDir | Out-Null

$SdkManager = Join-Path $SdkDir 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $SdkManager)) {
    Info 'instaluję cmdline-tools'
    $Zip = Join-Path $ToolsDir 'cmdline-tools.zip'
    $ok = $false
    foreach ($build in @(13114758, 12700392, 11076708, 10406996, 9477386)) {
        $u = "https://dl.google.com/android/repository/commandlinetools-win-${build}_latest.zip"
        Info "próbuję: $u"
        if (Get-File $u $Zip) { $ok = $true; break }
    }
    if (-not $ok) { Fail 'nie udało się pobrać cmdline-tools: https://developer.android.com/studio#command-line-tools-only' }
    $Tmp = Join-Path $ToolsDir 'ct-extract'
    if (Test-Path $Tmp) { Remove-Item -Recurse -Force $Tmp }
    Expand-Archive -Path $Zip -DestinationPath $Tmp -Force
    $Src = Join-Path $Tmp 'cmdline-tools'
    $Dest = Join-Path $SdkDir 'cmdline-tools\latest'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Dest) | Out-Null
    if (Test-Path $Dest) { Remove-Item -Recurse -Force $Dest }
    Move-Item -Path $Src -Destination $Dest
    Remove-Item -Force $Zip; Remove-Item -Recurse -Force $Tmp
}

$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
Info "SDK: $SdkDir"

Info 'akceptuję licencje SDK'
$yes = ("y`n" * 40)
$yes | & $SdkManager --sdk_root="$SdkDir" --licenses 2>&1 | Out-Null

$Platform = 'platforms;android-36'
$BuildTools = 'build-tools;36.0.0'
Info "instaluję: platform-tools, $Platform, $BuildTools (ok. 300 MB, jednorazowo)"
& $SdkManager --sdk_root="$SdkDir" 'platform-tools' $Platform $BuildTools 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail 'sdkmanager nie zainstalował pakietów – sprawdź połączenie z dl.google.com' }

$Escaped = $SdkDir.Replace('\', '\\').Replace(':', '\:')
Set-Content -Path (Join-Path $Root 'local.properties') -Value "sdk.dir=$Escaped" -Encoding ASCII
Info "zapisano local.properties → sdk.dir=$SdkDir"

# --- 3. Klucz podpisywania ----------------------------------------------------------------------
Step '3/6' 'Klucz podpisywania'
$KeyDir = if ($env:TERMINOWNIK_KEY_DIR) { $env:TERMINOWNIK_KEY_DIR } else { Join-Path $HOME 'keys\terminownik' }
$Props = Join-Path $Root 'keystore.properties'
$Store = Join-Path $KeyDir 'terminownik-release.jks'

if ($env:TERMINOWNIK_STORE_FILE -and $env:TERMINOWNIK_STORE_PASSWORD -and $env:TERMINOWNIK_KEY_ALIAS -and $env:TERMINOWNIK_KEY_PASSWORD) {
    Info 'źródło klucza: zmienne środowiskowe właściciela'
} elseif (Test-Path $Props) {
    Info 'źródło klucza: istniejący keystore.properties (nie nadpisuję)'
} elseif (Test-Path (Join-Path $KeyDir 'keystore.properties')) {
    Copy-Item (Join-Path $KeyDir 'keystore.properties') $Props -Force
    Info "źródło klucza: $KeyDir"
} else {
    Info "tworzę trwały klucz w $KeyDir (hasła losowe, nie są drukowane)"
    New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
    if (Test-Path $Store) { Fail "klucz już istnieje: $Store – nie wolno go nadpisać" }
    $chars = (48..57) + (65..90) + (97..122)
    $StorePassword = -join (1..28 | ForEach-Object { [char](Get-Random -InputObject $chars) })
    $KeyPassword = -join (1..28 | ForEach-Object { [char](Get-Random -InputObject $chars) })
    & "$JdkDir\bin\keytool.exe" -genkeypair -keystore $Store -storetype JKS `
        -alias terminownik-release -keyalg RSA -keysize 4096 -validity 10950 `
        -storepass $StorePassword -keypass $KeyPassword `
        -dname 'CN=Terminownik, OU=Release, O=Wlasciciel aplikacji, L=Katowice, ST=Slaskie, C=PL' 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'keytool nie utworzył klucza' }
    $lines = @(
        "storeFile=$($Store.Replace('\', '\\').Replace(':', '\:'))",
        "storePassword=$StorePassword",
        'keyAlias=terminownik-release',
        "keyPassword=$KeyPassword"
    )
    Set-Content -Path (Join-Path $KeyDir 'keystore.properties') -Value $lines -Encoding ASCII
    Copy-Item (Join-Path $KeyDir 'keystore.properties') $Props -Force
}
Info "WAŻNE: zbackupuj OFFLINE katalog $KeyDir – bez niego nie zaktualizujesz aplikacji."

# --- 4. Build ------------------------------------------------------------------------------------
Step '4/6' 'Kompilacja, podpisanie i weryfikacja'
if ($Clean) { & .\gradlew.bat --no-daemon clean 2>&1 | Out-Null }

$Log = Join-Path $env:TEMP 'terminownik-build.log'
$Tasks = @(':app:assembleRelease')
& .\gradlew.bat --no-daemon --stacktrace --continue @Tasks 2>&1 | Tee-Object -FilePath $Log
$BuildExit = $LASTEXITCODE

$Apk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'dist') | Out-Null

if ($BuildExit -ne 0 -or -not (Test-Path $Apk)) {
    $FailLog = Join-Path $Root 'dist\BUILD_FAILURE_LOG.txt'
    "# Log nieudanego buildu - $(Get-Date -Format o)" | Set-Content $FailLog -Encoding UTF8
    Get-Content $Log -Tail 400 | Add-Content $FailLog -Encoding UTF8
    Info "log błędu: $FailLog"
    Get-Content $Log -Tail 60
    Fail 'kompilacja się nie powiodła – wklej treść dist\BUILD_FAILURE_LOG.txt w odpowiedzi'
}

if (-not $SkipTests) {
    Step '4b/6' 'Lint i testy (informacyjnie – APK już istnieje)'
    $Qa = @(':app:lintRelease', ':app:testReleaseUnitTest')
    & .\gradlew.bat --no-daemon --continue @Qa 2>&1 | Tee-Object -FilePath $Log -Append
    $QaExit = $LASTEXITCODE
    Info "kod wyjściowy lint/testy: $QaExit"
} else { $QaExit = 0 }

# --- 5. Weryfikacja ------------------------------------------------------------------------------
Step '5/6' 'Weryfikacja artefaktu'
$BuildToolsDir = Get-ChildItem (Join-Path $SdkDir 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$ApkSigner = Join-Path $BuildToolsDir.FullName 'apksigner.bat'
$Aapt2 = Join-Path $BuildToolsDir.FullName 'aapt2.exe'

$Dist = Join-Path $Root 'dist'
$OutApk = Join-Path $Dist 'terminownik-release.apk'
Copy-Item $Apk $OutApk -Force
$Sha = (Get-FileHash -Path $OutApk -Algorithm SHA256).Hash.ToLower()
$Size = (Get-Item $OutApk).Length
Set-Content -Path (Join-Path $Dist 'SHA256SUMS.txt') -Value "$Sha  terminownik-release.apk" -Encoding ASCII

$Verify = & $ApkSigner verify --verbose --print-certs $OutApk 2>&1 | Out-String
$Badging = & $Aapt2 dump badging $OutApk 2>&1 | Out-String
$Perms = & $Aapt2 dump permissions $OutApk 2>&1 | Out-String

if ($Perms -match 'android\.permission\.INTERNET') { Fail 'w uprawnieniach APK jest INTERNET – aplikacja ma działać offline' }

$Fence = '```'
$BadgingLines = ($Badging -split "`n" | Where-Object { $_ -match 'package:|sdkVersion|targetSdkVersion|application-label|native-code' }) -join "`n"
$Report = @"
# Raport weryfikacji wydania

* data: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss')) UTC
* system: Windows ($env:PROCESSOR_ARCHITECTURE)
* rozmiar: $Size bajtów
* SHA-256: $Sha

## apksigner verify --verbose --print-certs
$Fence
$Verify
$Fence

## aapt2 dump badging (wybrane)
$Fence
$BadgingLines
$Fence

## Uprawnienia
$Fence
$Perms
$Fence

## Jakość kodu
* kod wyjściowy lint/testy: $QaExit
"@
Set-Content -Path (Join-Path $Dist 'VERIFY_REPORT.md') -Value $Report -Encoding UTF8

Info "APK:     $OutApk"
Info "rozmiar: $Size bajtów ($([math]::Round($Size / 1MB, 1)) MB)"
Info "SHA-256: $Sha"
Info "raport:  $(Join-Path $Dist 'VERIFY_REPORT.md')"
Info ''
Info 'Instalacja na telefonie:'
Info "  $(Join-Path $SdkDir 'platform-tools\adb.exe') install -r dist\terminownik-release.apk"
Info '  (albo skopiuj plik na telefon i otwórz go – zezwól na instalację z nieznanego źródła)'

Step '6/6' "Gotowe (czas: $([int]((Get-Date) - $Start).TotalMinutes) min)"
