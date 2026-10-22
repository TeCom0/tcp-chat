#!/bin/sh
# Starts the chat client:  ./run-client.sh 192.168.1.20 3001   (no arguments: client.properties decides)
cd "$(dirname "$0")" && exec java -jar TCPClient.jar "$@"
