@echo off
setlocal
cd /d "%~dp0"
if not exist "java\bin\java.exe" (
 echo Java portatil nao encontrado. Execute CRIAR_PORTATIL.bat na pasta do projeto primeiro.
 pause
 exit /b 1
)
if not exist "app\formula-projeto.jar" (
 echo Aplicativo nao encontrado.
 pause
 exit /b 1
)
start "" "http://localhost:8080"
echo Sistema iniciando. Mantenha esta janela aberta enquanto usar o site.
echo Caso a porta 8080 ja esteja ocupada, feche a outra instancia.
"%~dp0java\bin\java.exe" -cp "app\formula-projeto.jar;app\lib\*" formula.AppWeb
if errorlevel 1 (
 echo.
 echo O sistema nao iniciou. Veja o erro acima.
 pause
)
