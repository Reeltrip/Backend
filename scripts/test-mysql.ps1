[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$containerName = 'reeltrip-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$created = $false
$previous = @{}
$databases = [ordered]@{
    REELTRIP_MYSQL_URL = 'reeltrip_issue9'
    REELTRIP_AUTH_MYSQL_URL = 'reeltrip_issue12'
    REELTRIP_SOURCE_MYSQL_URL = 'reeltrip_issue16'
    REELTRIP_JOURNEY_MYSQL_URL = 'reeltrip_journey'
    REELTRIP_ANALYSIS_MYSQL_URL = 'reeltrip_analysis'
}
Push-Location $repoRoot
try {
    # 외부 DB 주소는 받지 않고, 새 일회용 컨테이너만 사용해요.
    docker run --detach --name $containerName --publish '127.0.0.1::3306' -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.0 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '테스트 MySQL을 만들지 못했어요.' }
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $containerName mysql '--protocol=tcp' '--host=127.0.0.1' -e 'SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw '테스트 MySQL 시작 시간이 지났어요.' }
    $binding = (docker port $containerName 3306/tcp).Trim()
    if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:([0-9]+)$') { throw '루프백 포트를 확인하지 못했어요.' }
    $dbPort = $Matches[1]
    foreach ($entry in $databases.GetEnumerator()) {
        $database = $entry.Value
        docker exec $containerName mysql -e "CREATE DATABASE $database CHARACTER SET utf8mb4"
        if ($LASTEXITCODE -ne 0) { throw '빈 테스트 DB 생성 실패' }
        Get-Content (Join-Path $repoRoot 'docs/schema.sql') -Raw -Encoding utf8 |
            docker exec -i $containerName mysql --default-character-set=utf8mb4 $database
        if ($LASTEXITCODE -ne 0) { throw '수동 테스트 스키마 적용 실패' }
        $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        $url = "jdbc:mysql://127.0.0.1:$dbPort/${database}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
        [Environment]::SetEnvironmentVariable($entry.Key, $url, 'Process')
    }
    & (Join-Path $repoRoot 'gradlew.bat') --no-daemon --rerun-tasks test bootJar verifyMockIsolation
    if ($LASTEXITCODE -ne 0) { throw '통합 테스트가 실패했어요. build/reports/tests/test를 확인해요.' }
} finally {
    foreach ($entry in $previous.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    if ($created -and $containerName -match '^reeltrip-test-[a-f0-9]{12}$') {
        docker rm --force $containerName | Out-Null
    }
    Pop-Location
}
