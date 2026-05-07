param(
    [string]$SonarHostUrl = $env:SONAR_HOST_URL,
    [string]$SonarToken = $env:SONAR_TOKEN
)

if ([string]::IsNullOrWhiteSpace($SonarHostUrl) -or [string]::IsNullOrWhiteSpace($SonarToken)) {
    Write-Error "SONAR_HOST_URL et SONAR_TOKEN doivent etre definis."
    exit 1
}

$services = @(
    "apiGateway",
    "config-server",
    "eureka",
    "microservices/users",
    "microservices/quiz",
    "microservices/privetcours",
    "microservices/messaging",
    "microservices/forum",
    "microservices/exams",
    "microservices/courses",
    "microservices/ai-assistant-service",
    "microservices/adaptive-learning"
)

foreach ($service in $services) {
    Write-Host "Running SonarQube for $service"
    $svcPath = Join-Path $PSScriptRoot ".." $service
    Push-Location $svcPath
    # Use mvnw.cmd (Windows Maven wrapper) — avoids requiring mvn on PATH
    .\mvnw.cmd clean verify sonar:sonar `
        "-Dsonar.projectKey=smartlingua" `
        "-Dsonar.projectName=SmartLingua" `
        "-Dsonar.host.url=$SonarHostUrl" `
        "-Dsonar.token=$SonarToken" `
        "-Dsonar.java.source=17" `
        "-Dsonar.java.target=17"
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "SonarQube analysis failed for $service (exit $LASTEXITCODE). Continuing..."
    }
    Pop-Location
}

Write-Host "SonarQube analysis completed for all backend services."
