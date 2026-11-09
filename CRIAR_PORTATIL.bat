@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0CRIAR_PORTATIL.ps1"
if errorlevel 1 (
 echo.
 echo FALHA ao criar a versao portatil. Veja a mensagem acima.
 pause
 exit /b 1
)
echo.
echo SUCESSO! Copie a pasta PORTATIL_WINDOWS para o pendrive.
pause
