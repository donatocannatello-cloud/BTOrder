@echo off
setlocal EnableExtensions
title Cacciatore Affari
cd /d "%~dp0"

rem ============================================================
rem  Cacciatore Affari - pannello di controllo
rem  Doppio clic per avviare. Al primo avvio crea l'ambiente
rem  Python (.venv), installa le dipendenze e prepara config.json.
rem ============================================================

set "VENV_PY=%~dp0.venv\Scripts\python.exe"
set "REPORT=%~dp0report\annunci.html"

if exist "%VENV_PY%" goto :config

echo Primo avvio: creo l'ambiente Python...
set "PYTHON="
where py >nul 2>&1 && py -3.11 --version >nul 2>&1 && set "PYTHON=py -3.11"
if not defined PYTHON where py >nul 2>&1 && py -3 --version >nul 2>&1 && set "PYTHON=py -3"
if not defined PYTHON where python >nul 2>&1 && set "PYTHON=python"
if not defined PYTHON (
    echo.
    echo [ERRORE] Python non trovato. Installa Python 3.11 o superiore da
    echo          https://www.python.org/downloads/  ^(spunta "Add python.exe to PATH"^)
    pause
    exit /b 1
)
%PYTHON% -m venv .venv || (echo [ERRORE] creazione .venv fallita & pause & exit /b 1)
"%VENV_PY%" -m pip install --upgrade pip -q
"%VENV_PY%" -m pip install -r requirements.txt -q || (echo [ERRORE] installazione dipendenze fallita & pause & exit /b 1)
echo Ambiente pronto.

:config
if exist "config.json" goto :menu
copy /y "config.example.json" "config.json" >nul
echo.
echo Ho creato config.json dal modello. Inserisci token e chat_id di Telegram,
echo salva e chiudi il Blocco note per continuare.
start /wait notepad "config.json"

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
