@echo off
java -jar "%~dp0leo-trajectory-presentation.jar" gui --presentation %*
if errorlevel 1 pause
