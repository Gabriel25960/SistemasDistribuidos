#!/usr/bin/env bash
# Compila todo o projeto para a pasta ./out  (requer JDK 11+)
set -e
cd "$(dirname "$0")/.."
rm -rf out
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")
echo "Compilado com sucesso em ./out"
