@echo off
REM Compila todo o projeto para a pasta out (requer JDK 11+)
cd /d "%~dp0.."
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
del sources.txt
echo Compilado com sucesso em .\out
