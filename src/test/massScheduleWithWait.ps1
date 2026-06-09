for ($i=1; $i -le 30; $i++) {
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/tasks" `
            -Method POST `
            -ContentType "application/json" `
            -Body "{`"scriptPath`": `"/scripts/bulk_$i.sh`", `"scheduledTime`": `"2026-06-07T19:00:00`"}"

        Write-Host "Task $i : 201 Created (ID: $($response.id))" -ForegroundColor Green
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "Task $i : $statusCode REJECTED (Rate Limit Exceeded)" -ForegroundColor Yellow
    }

    # If we've hit a multiple of 10 (and we aren't at the very end), sleep for 5 seconds
    if ($i % 10 -eq 0 -and $i -ne 30) {
        Write-Host "--- Pausing for 5 seconds to let rate limiter refill ---" -ForegroundColor Cyan
        Start-Sleep -Seconds 5
    }
}