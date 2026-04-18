# Arduino Build and Upload Script for Antigravity

$ARDUINO_CLI = "C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
$FQBN = "arduino:samd:nano_33_iot"
$PORT = "COM4"
$SKETCH = "aduino.ino"

Write-Host "--- Step 1: Compiling Sketch: $SKETCH ---" -ForegroundColor Cyan
& $ARDUINO_CLI compile --fqbn $FQBN $SKETCH

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "--- Step 2: Uploading to $FQBN on $PORT ---" -ForegroundColor Cyan
& $ARDUINO_CLI upload -p $PORT --fqbn $FQBN $SKETCH

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Upload failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "--- Step 3: Starting Serial Monitor (Ctrl+C to stop) ---" -ForegroundColor Yellow
& $ARDUINO_CLI monitor -p $PORT
