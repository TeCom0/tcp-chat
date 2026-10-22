#!/bin/sh
# Starts the chat server (add --console for text mode). Settings: server.properties in this folder.
cd "$(dirname "$0")" && exec java -jar TCPServer.jar "$@"
