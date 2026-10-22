@echo off
REM Starts the chat server. Double-click it, or run: run-server.bat --console   (text mode)
REM Settings: edit server.properties in this folder, then restart (no recompiling).
java -jar "%~dp0TCPServer.jar" %*
if errorlevel 1 pause
