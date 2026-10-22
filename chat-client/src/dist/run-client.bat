@echo off
REM Starts the chat client. Double-click it, or run: run-client.bat 192.168.1.20 3001
REM Without arguments, client.properties in this folder decides where to connect.
java -jar "%~dp0TCPClient.jar" %*
if errorlevel 1 pause
