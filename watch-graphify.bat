@echo off
echo Starting Graphify Watchdog...
set PYTHONUNBUFFERED=1
graphify watch . --debounce 2
pause
