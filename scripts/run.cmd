@echo off
setlocal
cd /d "%~dp0.."

call mvn -q -DskipTests package
if errorlevel 1 exit /b 1

java -jar target\linkshorter.jar %*
