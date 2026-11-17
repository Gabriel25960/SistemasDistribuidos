@echo off
REM Sobe os 8 nos do anel, cada um em uma janela propria.
REM Depois, em UMA das janelas, digite:  election
REM Para derrubar o coordenador (processo 8), va na janela "N8" e digite:  quit
cd /d "%~dp0.."
if not exist out call scripts\build.bat
set CFG=config\anel.properties

for %%i in (1 2 3 4 5 6 7 8) do start "N%%i" java -cp out anel.Node --id %%i --config %CFG%

echo.
echo 8 janelas abertas (N1 a N8).
echo  1) Na janela N3 digite: election
echo  2) Na janela N8 digite: quit
echo  3) Observe a nova eleicao nas outras janelas.
echo Tambem e possivel usar: java -cp out anel.Control --to 3 --cmd election
pause
