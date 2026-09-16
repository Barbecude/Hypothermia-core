Write-Host "Starting Graphify Watchdog..." -ForegroundColor Cyan
$env:PYTHONUNBUFFERED = "1"
graphify watch . --debounce 2
