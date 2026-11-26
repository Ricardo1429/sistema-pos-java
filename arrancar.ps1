# Arranca RestoBar POS en http://localhost:8081
# Uso:  .\arrancar.ps1
# El perfil 'dev' ya se activa desde pom.xml para spring-boot:run.

$ErrorActionPreference = 'Stop'

# 1) Spring Boot 3.5 exige Java 17+. El JAVA_HOME por defecto de esta
#    máquina apunta a un JRE 1.8 y el plugin spring-boot:run falla.
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2'

# 2) dev exige DB_PASSWORD. Se lee de la configuración local; nunca se
#    escribe en el proyecto. Si ya la exportaste, se respeta.
if (-not $env:DB_PASSWORD) {
    $cfg = Join-Path $env:USERPROFILE '.config\opencode\AGENTS.md'
    if (-not (Test-Path $cfg)) {
        throw "No encontré $cfg. Define DB_PASSWORD antes de arrancar."
    }
    $match = [regex]::Match((Get-Content $cfg -Raw),
        '### PostgreSQL Local[\s\S]*?Password:\s*`([^`]+)`')
    if (-not $match.Success) {
        throw 'No pude leer la password de PostgreSQL local de la configuración.'
    }
    $env:DB_PASSWORD = $match.Groups[1].Value
}
$env:DB_URL = 'jdbc:postgresql://localhost:5432/restobar'
$env:DB_USER = 'postgres'
$env:SEED_PASSWORD = 'restobar2026'

Write-Host 'Arrancando RestoBar POS en http://localhost:8081 ...' -ForegroundColor Cyan
& .\mvnw.cmd -B spring-boot:run
