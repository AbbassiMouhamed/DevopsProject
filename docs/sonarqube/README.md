# SmartLingua — SonarQube: Connecting Real Project Data

This guide shows you exactly how to wire SonarQube to the live codebase, generate
real coverage reports, and read the quality gate results.

---

## 1. Spin Up SonarQube Locally (Docker)

```bash
docker run -d \
  --name sonarqube \
  -p 9000:9000 \
  -v sonarqube_data:/opt/sonarqube/data \
  -v sonarqube_extensions:/opt/sonarqube/extensions \
  sonarqube:lts-community
```

Open http://localhost:9000  
Default credentials → **admin / admin** (you will be forced to change this on first login).

---

## 2. Create a Project and Generate a Token

1. **Administration → Projects → Create Project → Manually**
2. **Project key**: `smartlingua`  
   **Display name**: `SmartLingua`
3. **Set up → Locally → Generate token**
   - Token type: **Project Analysis Token**
   - Name: `smartlingua-ci`
   - Copy the token — you will never see it again.

---

## 3. Configure Environment Variables

Never hard-code the token. Export it in your shell or CI secret store:

```bash
# .env or shell profile — DO NOT commit this file
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN=sqp_a67ea444c994ca2fef4bc8d57009b49e052c88f2
```

The `sonar-project.properties` files in this repo already reference these via
`${env.SONAR_HOST_URL}` and `${env.SONAR_TOKEN}`, so no file editing is needed.

---

## 4. Run Analysis — Backend (all microservices)

```powershell
# From the repo root on Windows
$env:SONAR_HOST_URL = "http://localhost:9000"
$env:SONAR_TOKEN    = "squ_xxxxxxxxxxxxxxxxxxxx"

.\backend\scripts\run-sonar.ps1
```

What this script does:

- Iterates every backend module (apiGateway, config-server, eureka, all microservices)
- Runs `mvn clean verify sonar:sonar` for each — this compiles, runs tests, generates
  JaCoCo XML coverage, then ships everything to SonarQube
- Each module is uploaded as a separate component under the `smartlingua` project

### Run a single module manually

```bash
cd backend/microservices/users
./mvnw clean verify sonar:sonar \
  -Dsonar.projectKey=smartlingua \
  -Dsonar.projectName="SmartLingua" \
  -Dsonar.host.url=$SONAR_HOST_URL \
  -Dsonar.token=$SONAR_TOKEN
```

---

## 5. Run Analysis — Frontend (Angular)

```bash
cd frontend

# Install deps
npm ci

# Run tests with coverage
npm run test -- --no-watch --code-coverage --browsers=ChromeHeadless

# Install sonar-scanner CLI if not present
npm install -g @sonar/scanner   # formerly sonarqube-scanner

# Ship results
sonar-scanner \
  -Dsonar.projectKey=smartlingua \
  -Dsonar.projectName="SmartLingua" \
  -Dsonar.sources=src \
  -Dsonar.tests=src \
  -Dsonar.test.inclusions="**/*.spec.ts" \
  -Dsonar.javascript.lcov.reportPaths=coverage/smartlingua/lcov.info \
  -Dsonar.host.url=$SONAR_HOST_URL \
  -Dsonar.token=$SONAR_TOKEN
```

---

## 6. Where to See the Real Data in the UI

| What you want to see   | Path in SonarQube UI                              |
| ---------------------- | ------------------------------------------------- |
| Overall quality gate   | Projects → SmartLingua → **Overview**             |
| Code coverage %        | Overview → **Coverage** widget                    |
| Line-by-line coverage  | Issues → source file → highlighted lines          |
| Bugs / vulnerabilities | Overview → **Bugs** / **Vulnerabilities** widgets |
| Code smells            | Overview → **Code Smells**                        |
| Security hotspots      | **Security Hotspots** tab                         |
| Per-file breakdown     | **Code** tab → drill into any module              |
| Duplications           | Overview → **Duplications** widget                |
| Trends over time       | **Activity** tab → select measures to chart       |

---

## 7. Quality Gate — What "Pass" Means

The default Sonar Way gate fails the build if **any new code** introduces:

| Condition                      | Threshold |
| ------------------------------ | --------- |
| New Bugs                       | > 0       |
| New Vulnerabilities            | > 0       |
| New Security Hotspots reviewed | < 100%    |
| New Code Coverage              | < 80%     |
| New Duplicated Lines           | > 3%      |

In Jenkins the pipeline uses `waitForQualityGate()` after `withSonarQubeEnv(...)`.
If the gate fails the build turns red and the deploy stage is skipped.

---

## 8. Jenkins Integration (CI server)

In Jenkins:

1. **Manage Jenkins → Plugins** → install **SonarQube Scanner**
2. **Manage Jenkins → System → SonarQube servers**
   - Name: `sonarqube`
   - URL: `http://<sonar-host>:9000`
   - Token: add as a _Secret text_ credential, then select it here
3. In `Jenkinsfile-backend` the stage is already wired:

```groovy
stage('SonarQube Backend') {
  steps {
    withSonarQubeEnv('sonarqube') {   // ← must match the name in step 2
      sh 'mvn -f backend/apiGateway/pom.xml clean verify sonar:sonar ...'
    }
  }
}
```

---

## 9. Troubleshooting

| Symptom                             | Cause / Fix                                                               |
| ----------------------------------- | ------------------------------------------------------------------------- |
| `Project was never analyzed`        | Analysis has not run yet. Run `mvn sonar:sonar` manually first.           |
| Coverage shows 0%                   | JaCoCo XML not generated. Add `maven-surefire` + `jacoco-maven-plugin`.   |
| `401 Unauthorized`                  | Wrong or expired token. Regenerate in Administration → Security → Tokens. |
| `sonar.login` deprecation warning   | Replace `sonar.login` with `sonar.token` (already fixed in this repo).    |
| Quality gate stuck on "In Progress" | Webhook not configured. See SonarQube → Administration → Webhooks.        |
| `Could not find or load main class` | Maven wrapper not executable. Run `chmod +x mvnw` on Linux/Mac.           |
