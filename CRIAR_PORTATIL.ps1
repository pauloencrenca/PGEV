$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Run($exe, [string[]]$arguments) {
  & $exe @arguments
  if ($LASTEXITCODE -ne 0) { throw "Falha ao executar $exe (codigo $LASTEXITCODE)" }
}
try {
  if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven nao encontrado. Execute este arquivo no PC em que o Maven ja funciona.' }
  if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\jlink.exe'))) {
    $possible = @(
      (Join-Path $env:USERPROFILE '.jdk\jdk-21'),
      (Get-ChildItem "$env:LOCALAPPDATA\Programs\Eclipse Adoptium" -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName),
      (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName)
    ) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\jlink.exe')) }
    if ($possible.Count -gt 0) { $env:JAVA_HOME = $possible[0] }
  }
  if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\jlink.exe'))) { throw 'JDK 21 nao encontrado. Configure JAVA_HOME apontando para a pasta do JDK 21.' }
  $releaseFile = Join-Path $env:JAVA_HOME 'release'
  if (-not (Test-Path $releaseFile) -or (Get-Content $releaseFile -Raw) -notmatch 'JAVA_VERSION="21(?:\.|")') { throw 'JAVA_HOME deve apontar para um JDK 21. Atualize JAVA_HOME e tente novamente.' }
  $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
  Write-Host 'Compilando projeto e baixando dependencias (internet necessaria nesta etapa)...'
  Run 'mvn' @('-B','-DskipTests','clean','package','dependency:copy-dependencies','-DoutputDirectory=target/portable-libs')
  $out = Join-Path $PSScriptRoot 'PORTATIL_WINDOWS'
  if (Test-Path $out) { Remove-Item $out -Recurse -Force }
  New-Item -ItemType Directory -Force -Path (Join-Path $out 'app\lib') | Out-Null
  Copy-Item 'target\formula-projeto-1.0.jar' (Join-Path $out 'app\formula-projeto.jar')
  Copy-Item 'target\portable-libs\*.jar' (Join-Path $out 'app\lib\')
  Write-Host 'Criando Java portatil para Windows (pode demorar)...'
  $jlink = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
  Run $jlink @('--add-modules','java.base,java.sql,java.desktop,java.logging,java.management,java.naming,java.net.http,jdk.httpserver,jdk.unsupported','--strip-debug','--no-header-files','--no-man-pages','--output',(Join-Path $out 'java'))
  Copy-Item 'INICIAR.bat' (Join-Path $out 'INICIAR.bat')
  Copy-Item 'LEIA-ME-PORTATIL.txt' (Join-Path $out 'LEIA-ME.txt')
  Write-Host "Pronto: $out" -ForegroundColor Green
} catch {
  Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
  exit 1
}

