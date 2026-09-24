@echo off
setlocal EnableExtensions
title Cacciatore Affari
cd /d "%~dp0"

rem ============================================================
rem  Cacciatore Affari - pannello di controllo
rem  Doppio clic per avviare: apre il pannello di controllo nel
rem  browser. Al primo avvio scarica il programma (se manca), crea
rem  l'ambiente Python (.venv), installa le dipendenze e prepara
rem  config.json.
rem ============================================================

rem Nuova versione di questo file scaricata dal pannello: la si installa e riavvia.
rem (il blocco tra parentesi viene letto per intero prima di essere eseguito)
if exist "%~f0.nuovo" (
    move /y "%~f0.nuovo" "%~f0" >nul
    "%~f0"
    exit /b
)

set "ZIP_URL=https://github.com/donatocannatello-cloud/BTOrder/archive/refs/heads/claude/cacciatore-affari-bot-mh8x85.zip"

rem --- 1. File del programma ------------------------------------
if exist "main.py" goto :ambiente
if exist "cacciatore_affari\main.py" (
    cd /d "%~dp0cacciatore_affari"
    goto :ambiente
)
echo I file del programma non sono in questa cartella:
echo   %CD%
echo.
set "RISP=S"
set /p "RISP=Li scarico ora da GitHub? [S/n]: "
if /i not "%RISP%"=="S" exit /b 1
call :scarica || (pause & exit /b 1)

rem --- 2. Ambiente Python -----------------------------------------
:ambiente
set "VENV_PY=%CD%\.venv\Scripts\python.exe"
set "REPORT=%CD%\report\annunci.html"
if exist "%VENV_PY%" goto :dipendenze

echo Creo l'ambiente Python...
set "PYTHON="
where py >nul 2>&1 && py -3.11 --version >nul 2>&1 && set "PYTHON=py -3.11"
if not defined PYTHON where py >nul 2>&1 && py -3 --version >nul 2>&1 && set "PYTHON=py -3"
if not defined PYTHON where python >nul 2>&1 && set "PYTHON=python"
if not defined PYTHON goto :no_python
%PYTHON% -m venv .venv || (echo [ERRORE] creazione .venv fallita & pause & exit /b 1)

:dipendenze
rem il file .venv\installato.ok viene creato solo se pip termina senza errori
if exist ".venv\installato.ok" goto :config
echo Installo le dipendenze...
"%VENV_PY%" -m pip install --upgrade pip -q
"%VENV_PY%" -m pip install -r requirements.txt -q || (echo [ERRORE] installazione dipendenze fallita & pause & exit /b 1)
echo ok> ".venv\installato.ok"
echo Ambiente pronto.

rem --- 3. Configurazione -------------------------------------------
:config
if not exist "config.json" copy /y "config.example.json" "config.json" >nul

rem --- 4. Pannello di controllo nel browser -------------------------
echo.
echo Avvio il pannello di controllo nel browser...
echo Lascia aperta questa finestra: chiudendola si ferma il programma.
echo.
"%VENV_PY%" pannello.py
if errorlevel 1 (
    echo.
    echo [ERRORE] Il pannello si e' chiuso con un errore ^(vedi sopra^).
    pause
)
exit /b 0

rem --- Scarica il programma da GitHub nella cartella corrente ---------
rem Non sovrascrive config.json, data\annunci.json, i log ne' questo .bat.
:scarica
echo Scarico il programma da GitHub...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$z=Join-Path $env:TEMP 'cacciatore_affari.zip'; $d=Join-Path $env:TEMP 'cacciatore_affari_zip';" ^
  "Invoke-WebRequest -UseBasicParsing -Uri $env:ZIP_URL -OutFile $z;" ^
  "if (Test-Path $d) { Remove-Item -Recurse -Force $d };" ^
  "Expand-Archive -Path $z -DestinationPath $d;" ^
  "$src=(Get-ChildItem $d -Recurse -Directory -Filter cacciatore_affari | Select-Object -First 1).FullName;" ^
  "if (-not $src) { throw 'cartella cacciatore_affari non trovata nello ZIP' };" ^
  "Get-ChildItem -Force $src | Where-Object { $_.Name -ne 'AFFARI_MIEI.bat' } | Copy-Item -Destination (Get-Location) -Recurse -Force;" ^
  "Remove-Item -Recurse -Force $d, $z"
if errorlevel 1 (
    echo [ERRORE] Download non riuscito. Controlla la connessione a internet.
    exit /b 1
)
echo File del programma copiati in %CD%
exit /b 0

:no_python
echo.
echo [ERRORE] Python non trovato. Installa Python 3.11 o superiore da
echo          https://www.python.org/downloads/
echo          e durante l'installazione spunta "Add python.exe to PATH".
pause
exit /b 1
