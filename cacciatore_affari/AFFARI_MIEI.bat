@echo off
setlocal EnableExtensions
title Cacciatore Affari
cd /d "%~dp0"

rem ============================================================
rem  Cacciatore Affari - pannello di controllo
rem  Doppio clic per avviare. Al primo avvio scarica il programma
rem  (se manca), crea l'ambiente Python (.venv), installa le
rem  dipendenze e prepara config.json.
rem ============================================================

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
if exist "config.json" goto :menu
copy /y "config.example.json" "config.json" >nul
echo.
echo Ho creato config.json dal modello. Inserisci token e chat_id di Telegram,
echo salva e chiudi il Blocco note per continuare.
start /wait notepad "config.json"

rem --- 4. Menu -----------------------------------------------------
:menu
cls
echo ============================================================
echo                     CACCIATORE AFFARI
echo ============================================================
echo.
echo   1. Scansiona ora e apri il monitor
echo   2. Apri il monitor (senza scansione)
echo   3. Scansione di prova (nessun messaggio Telegram) e monitor
echo   4. Avvio continuo: scansione ogni N ore
echo   5. Modifica configurazione (config.json)
echo   6. Apri il file di log
echo   7. Esegui i test
echo   8. Aggiorna il programma da GitHub
echo   0. Esci
echo.
set "SCELTA="
set /p "SCELTA=Scelta: "

if "%SCELTA%"=="1" goto :scansiona
if "%SCELTA%"=="2" goto :monitor
if "%SCELTA%"=="3" goto :prova
if "%SCELTA%"=="4" goto :continuo
if "%SCELTA%"=="5" goto :modifica
if "%SCELTA%"=="6" goto :log
if "%SCELTA%"=="7" goto :test
if "%SCELTA%"=="8" goto :aggiorna
if "%SCELTA%"=="0" exit /b 0
goto :menu

:scansiona
"%VENV_PY%" main.py
goto :apri_dopo_scansione

:prova
"%VENV_PY%" main.py --dry-run
goto :apri_dopo_scansione

:apri_dopo_scansione
echo.
"%VENV_PY%" viewer.py
pause
goto :menu

:monitor
if exist "%REPORT%" (
    start "" "%REPORT%"
) else (
    "%VENV_PY%" viewer.py
)
goto :menu

:continuo
set "ORE=6"
set /p "ORE=Ogni quante ore? [6]: "
echo.
echo Avvio continuo ogni %ORE% ore. Il monitor si aggiorna a ogni ciclo:
echo riaprilo o ricarica la pagina ^(F5^) per vedere i dati nuovi.
echo Premi CTRL+C per fermare.
echo.
if exist "%REPORT%" start "" "%REPORT%"
"%VENV_PY%" main.py --loop %ORE%
pause
goto :menu

:modifica
start /wait notepad "config.json"
goto :menu

:log
if exist "logs\cacciatore_affari.log" (
    start "" notepad "logs\cacciatore_affari.log"
) else (
    echo Nessun log ancora: esegui prima una scansione.
    pause
)
goto :menu

:test
"%VENV_PY%" -m pip install -q pytest
"%VENV_PY%" -m pytest -q
pause
goto :menu

:aggiorna
call :scarica || (pause & goto :menu)
rem le dipendenze potrebbero essere cambiate
del ".venv\installato.ok" >nul 2>&1
"%VENV_PY%" -m pip install -r requirements.txt -q && echo ok> ".venv\installato.ok"
echo Programma aggiornato. config.json, dati e log non sono stati toccati.
pause
goto :menu

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
