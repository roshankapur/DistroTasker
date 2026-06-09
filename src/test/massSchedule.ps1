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
}