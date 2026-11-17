@echo off
REM Sobe 4 participantes (cada um em uma janela propria) e depois o coordenador.
cd /d "%~dp0.."
if not exist out call scripts\build.bat
set CFG=config\berkeley.properties

start "P1" java -cp out berkeley.Participant --id 1 --offset 30000 --config %CFG%
start "P2" java -cp out berkeley.Participant --id 2 --offset -45000 --config %CFG%
start "P3" java -cp out berkeley.Participant --id 3 --offset 12000 --config %CFG%
start "P4" java -cp out berkeley.Participant --id 4 --offset -8000 --config %CFG%

timeout /t 3 >nul
java -cp out berkeley.Coordinator --offset 5000 --config %CFG%
echo.
echo Feche as janelas P1 a P4 para encerrar os participantes.
pause
