# Zoho → Manatal Migration

Migração de candidatos do Zoho Recruit para o Manatal ATS via Spring Batch.

## Stack

- Java 21, Spring Boot 3.5.6
- Spring Web, Spring Batch, Spring Data JPA, Spring Security, Spring Mail, Spring Actuator
- Hibernate 6.6, H2 (dev) / PostgreSQL (prod)
- JWT (jjwt 0.12.6), Lombok, Jackson
- SpringDoc OpenAPI 2.8.6 (Swagger UI)
- Maven Wrapper

## Pré-requisitos

| Variável | Descrição |
|---|---|
| `ZOHO_CLIENT_ID` | Client ID do Zoho OAuth |
| `ZOHO_CLIENT_SECRET` | Client Secret do Zoho OAuth |
| `ZOHO_REFRESH_TOKEN` | Refresh Token do Zoho OAuth |
| `MANATAL_TOKEN` | API Token do Manatal |
| `JWT_SECRET` | Chave secreta JWT (mín. 256 bits / 32 chars) |
| `ADMIN_USERNAME` | Username para login (dev: `admin`) |
| `ADMIN_PASSWORD` | Password para login (dev: `admin`) |
| `SMTP_HOST` | Servidor SMTP para notificações email |
| `SMTP_PORT` | Porta SMTP (default: 587) |
| `SMTP_USER` | Utilizador SMTP |
| `SMTP_PASS` | Password SMTP |
| `SLACK_WEBHOOK_URL` | Webhook URL do Slack (opcional) |
| `ALERTS_ENABLED` | Activar notificações (`true`/`false`) |

> `ADMIN_PASSWORD` aceita password raw ou BCrypt hash (se começar com `$2a$`, `$2b$` ou `$2y$`).

## Executar

```powershell
$env:ZOHO_CLIENT_ID="..."; $env:ZOHO_CLIENT_SECRET="..."; $env:ZOHO_REFRESH_TOKEN="..."; $env:MANATAL_TOKEN="..."; $env:JWT_SECRET="minha-chave-secreta-com-256-bits"; $env:ADMIN_PASSWORD="admin"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

H2 Console: `http://localhost:8080/h2-console` (JDBC: `jdbc:h2:mem:migration`, user: `sa`, pass: vazio)

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Autenticação

A API usa **JWT** (Bearer token) para proteger todos os endpoints excepto:

- `POST /api/auth/login` — obter token
- `GET /api/files/{id}` — servir attachments (Manatal precisa)
- `GET /actuator/health` — health check
- Swagger UI e OpenAPI docs

### Obter token

```powershell
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"admin"}'
```

Resposta:
```json
{"token":"eyJhbGciOiJIUz...","expiresIn":"24h"}
```

### Usar token

```powershell
curl -H "Authorization: Bearer eyJhbGciOiJIUz..." http://localhost:8080/api/batch/report
```

O token expira em 24h (configurável via `migration.security.jwt-expiration-hours`).

## Endpoints

### Zoho (`/api/zoho`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/candidates` | Lista 10 candidatos do Zoho |
| GET | `/candidates/save` | Busca e salva JSON bruto na base |
| GET | `/candidates/{id}` | Busca candidato por ID |
| GET | `/candidates/{id}/attachments` | Lista attachments do candidato |
| GET | `/attachments/{id}` | Download binário de attachment |
| GET | `/candidates/{cid}/attachments/{aid}/save` | Download + store na base local |
| GET | `/interviews` | Busca uma entrevista (debug) |
| GET | `/applications` | Busca uma application (debug) |
| GET | `/applications/{id}/attachments` | Attachments da application |
| GET | `/tags` | Lista tags de um módulo |

### Manatal (`/api/manatal`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/candidates` | Busca primeiro candidato (debug) |
| GET | `/candidates/{id}` | Busca candidato por ID |
| GET | `/custom-fields` | Ver custom fields de um candidato |

### Migration (`/api/migration`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/candidates/{zohoId}/preview` | Preview dos dados mapeados |
| POST | `/candidates/{zohoId}/migrate` | Migra candidato único |
| GET | `/custom-fields/verify` | Verifica custom fields no Manatal |
| POST | `/candidates/{manatalId}/attachments/test` | Testa attachment |
| POST | `/candidates/{manatalId}/resume/test` | Testa resume |
| GET | `/tags/verify` | Verifica tag de migração no Zoho |

### Batch (`/api/batch`)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/run` | Executa batch completo |
| GET | `/report` | Resumo das migrações |
| GET | `/logs` | Logs de auditoria |

### Auth (`/api/auth`)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/login` | Obter token JWT |

### Files (`/api/files`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/{id}` | Servir attachment por ID |

## Fluxo Batch

```
LoadCandidatesStep → MigrateCandidateStep → TagZohoStep
```

1. **LoadCandidatesStep** (Tasklet): Busca candidatos no Zoho com tag `PendenteMigracao` (paginado), insere como `PENDENTE` no banco
2. **MigrateCandidateStep** (Chunk, size=1): Processa registos `PENDENTE` — fetch Zoho + applications + interviews, download attachments, transform, cria candidato + notas + structured info + attachments + resume no Manatal
3. **TagZohoStep** (Tasklet): Adiciona tag `Exported` no Zoho e remove `PendenteMigracao`

## Tags Zoho

| Tag | Finalidade |
|---|---|
| `PendenteMigracao` | Candidatos a migrar (filtro do batch) |
| `Exported` | Candidatos já migrados (adicionada ao migrar) |

## Configuração Batch

| Propriedade | Default | Descrição |
|---|---|---|
| `migration.batch.chunk-size` | 1 | Registos por chunk (dev=1, prod=50) |
| `migration.batch.retry-limit` | 3 | Tentativas para `ApiException` |
| `migration.batch.skip-limit` | 10 | Erros não-retryáveis tolerados |
| `migration.batch.max-per-run` | 500 | Máx. candidatos por execução |

### Fault Tolerance

- **Retry** (3x): `ApiException` — timeouts, 502 das APIs
- **Skip** (10): excepções inesperadas
- **Não skip**: `ApiException` e `NullPointerException`

### Rate Limiting

| API | Mecanismo | Config |
|---|---|---|
| Manatal | `throttle()` — `Thread.sleep` entre chamadas | `migration.manatal.rate-limit-ms: 600` |
| Zoho | Leitura de `X-RATE-LIMIT-REMAINING` nas respostas | 3 thresholds (warning <100, critical <20, halt ≤5) |

Zoho: warning loga alerta, critical faz pausa de 60s, halt lança excepção e interrompe o batch.

## Mapeamento Zoho → Manatal

### Campos nativos
| Manatal | Zoho |
|---|---|
| `full_name` | `Full_Name` |
| `email` | `Email` |
| `phone_number` | `Phone` |
| `candidate_location` | `Country` |
| `description` | `Candidate_Description_Summary` + `Salary_Notes` |
| `creator` / `owner` | **1193857** (fixo) |
| `country` | `Country` |
| `ccurrency` | `Currency` |
| `ecurrency` | `Expected_Currency` |

### Custom fields
| Campo | Tipo | Zoho |
|---|---|---|
| `canrelocate` | boolean | `Relocation` ("Yes"/"No" → bool) |
| `workvisaeucitizenship` | boolean | `WorkVisa` ("Yes"/"No" → bool) |
| `civilstatus` | string | `Civil_Status` |
| `availabilityweeks` | int | `Availability_Days / 7` |
| `numberofdependants` | int | `Number_of_Dependants` |
| `consent_to_rgpd` | string | `Consent_to_RGPD` |
| `additional_info` | string | `Additional_Information` |
| `csalary` | int | `Current_Salary` (parse) |
| `esalary` | int | `Expected_Salary` (parse) |
| `first_name` | string | `First_Name` |
| `last_name` | string | `Last_Name` |
| `city` | string | `City` |
| `salary_notes` | string | `Salary_Notes` |
| `linkedin` | string | `LinkedIn__s` (normalizado) |
| `skills` | array | `Stacks_LinkedIn` / `Skills` (filtrado) |

### Notas estruturadas
- `expectedsalary` e `yearsofexperience` — enviados como notas (não existem como custom fields no Manatal)
- **Interview notes**: dados de entrevista (type, round, interviewer, status, rating, feedback) formatados em nota estruturada

## Configuração

`application.yml`:

```yaml
migration:
  zoho:
    tag-name: ${ZOHO_TAG_NAME:PendenteMigracao}
    success-tag-name: ${ZOHO_SUCCESS_TAG_NAME:Exported}
    base-url: https://recruit.zoho.eu/recruit/v2
    date-start: ${MIGRATION_DATE_START:2026-04-01}
    date-end:   ${MIGRATION_DATE_END:2026-06-30}
    page-size: 200
    rate-limit-threshold: ${ZOHO_RATE_LIMIT_THRESHOLD:100}
  manatal:
    base-url: https://api.manatal.com/open/v3/
    rate-limit-ms: 600
  batch:
    chunk-size: 1         # dev; prod = 50
    retry-limit: 3
    skip-limit: 10
    max-per-run: 500
```

## Testes

```powershell
.\mvnw.cmd test
```

- 30 testes de mapper (`CandidateMapperTest`)
- 3 testes de job (`CandidateMigrationJobTest`)
- 1 H2ConsoleTest (integração, com sleep para inspeção manual)
- 1 ApplicationTests (context load)

## Notificações

No fim de cada execução do batch, a app envia um relatório automático via `BatchJobListener` se `migration.alerts.enabled=true`.

### Slack
1. Cria uma workspace em `slack.com`
2. Vai a `https://api.slack.com/apps` → **Create New App** → **From scratch**
3. Activa **Incoming Webhooks** → **Add New Webhook to Workspace**
4. Copia a URL e define como `SLACK_WEBHOOK_URL` no `.env`

### Email
Define as variáveis `SMTP_HOST`, `SMTP_USER`, `SMTP_PASS` no `.env` e `ALERT_EMAIL_TO` com o destinatário.

## Tratamento de Erros

| Situação | Ação |
|---|---|
| Timeout / rede | Retry 3x (configurável) |
| Erro no processor | Skip + log em `migration_log` |
| Erro no writer | Rollback + status=`ERRO` |
| Candidate já migrado | Idempotência: verifica `manatal_candidate_id` |
| Rate limit (429) | Aguarda `rate-limit-ms` + retry |
| Zoho daily cap ≤5 | Halt — lança excepção |
| Zoho daily cap <20 | Pausa 60s até recuperar |
| Zoho daily cap <100 | Log warning + alerta |

Todas as excepções devolvem JSON estruturado via `ErrorResponse` + `GlobalExceptionHandler` (400, 401, 403, 404, 502, 500).

## Limitações

- Attachments exigem URL pública (ngrok / S3 / VPS) — Manatal não aceita upload binário directo
- Activities do Manatal: endpoint read-only (GET apenas), dados de entrevista vão para notas
- Sem endpoint de paragem do batch (`POST /api/batch/stop`)

## Deploy Windows Server (VM)

### Pré-requisitos

- Windows Server 2012 R2 (Build 9600) ou superior
- PostgreSQL (nativo, winget ou installer)
- Java 21 (JDK ou JRE)
- IIS 8.5+ com ARR 3.0 + URL Rewrite 2.1
- winsw 3.x (https://github.com/winsw/winsw/releases)

### 1. Instalar PostgreSQL

```powershell
# Desinstalar PostgreSQL Docker local se existir (porta 5432 livre)
# Opção A — winget
winget install "PostgreSQL 17"

# Opção B — installer manual
# Download: https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
# Porta: 5432, Password: <definir>

# Criar base e utilizador
psql -U postgres -c "CREATE USER etl WITH PASSWORD 'etl123';"
psql -U postgres -c "CREATE DATABASE etl_zoho OWNER etl;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE etl_zoho TO etl;"
```

### 2. Instalar Java 21

```powershell
# Download: https://adoptium.net/temurin/releases/?version=21
# Instalar no diretório fixo, ex: C:\Program Files\Eclipse Adoptium\jdk-21.0.x
# Adicionar ao PATH
[Environment]::SetEnvironmentVariable("Path", "$env:Path;C:\Program Files\Eclipse Adoptium\jdk-21.0.x\bin", "Machine")
```

### 3. Configurar IIS + ARR + URL Rewrite

1. Server Manager → Add Roles and Features → Web Server (IIS)
2. Instalar **Application Request Routing 3.0**:
   - Download: https://www.iis.net/downloads/microsoft/application-request-routing
3. Instalar **URL Rewrite 2.1**:
   - Download: https://www.iis.net/downloads/microsoft/url-rewrite
4. Abrir IIS Manager:
   - Application Pools → Add → Nome: `ZohoManatal`, .NET CLR: No Managed Code, Managed Pipeline: Integrated
   - Sites → Default Web Site → Add Application → Alias: `zoho-migration`, Pool: `ZohoManatal`, Physical Path: `C:\app\zoho-manatal\www`
5. Configurar reverse proxy no `web.config` em `C:\app\zoho-manatal\www\web.config`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <system.webServer>
    <rewrite>
      <rules>
        <rule name="ReverseProxyInbound" stopProcessing="true">
          <match url="(.*)" />
          <action type="Rewrite" url="http://localhost:8080/{R:1}" />
        </rule>
      </rules>
    </rewrite>
    <urlCompression doStaticCompression="false" doDynamicCompression="false" />
  </system.webServer>
</configuration>
```

6. ARR: Enable proxy — IIS Manager → ARR → Server Proxy Settings → Enable proxy

### 4. Preparar aplicação

```powershell
# No ambiente de desenvolvimento:
.\scripts\deploy.ps1

# Isto gera:
# - target\app.jar
# - target\service.xml  (winsw config com env vars)
```

### 5. Copiar para VM

```powershell
# Criar estrutura na VM
New-Item -ItemType Directory -Path "C:\app\zoho-manatal\www" -Force
New-Item -ItemType Directory -Path "C:\app\zoho-manatal\logs" -Force

# Copiar ficheiros
# - target\app.jar          → C:\app\zoho-manatal\app.jar
# - target\service.xml      → C:\app\zoho-manatal\service.xml
# - winsw.exe               → C:\app\zoho-manatal\winsw.exe
# - scripts\init-db.sql     → C:\app\zoho-manatal\init-db.sql
```

### 6. Inicializar BD

```powershell
# Se for primeira vez, correr script schema + Spring Batch tables
psql -U etl -d etl_zoho -f C:\app\zoho-manatal\init-db.sql
```

### 7. Instalar e iniciar serviço

```powershell
# Nomear winsw com o mesmo nome do XML
Rename-Item C:\app\zoho-manatal\winsw.exe zoho-manatal-service.exe

# Instalar serviço
.\zoho-manatal-service.exe install

# Verificar
.\zoho-manatal-service.exe status

# Iniciar
.\zoho-manatal-service.exe start

# Logs em C:\app\zoho-manatal\logs\
```

### 8. Testar

```powershell
# Health check (via IIS reverse proxy)
curl http://localhost/zoho-migration/api/actuator/health

# Se domínio público configurado:
curl https://seudominio.com/api/actuator/health

# Testar batch
curl -X POST https://seudominio.com/api/batch/run `
  -H "Authorization: Bearer <token>"
```

### 9. Firewall

- Abrir porta 8080 apenas para localhost (IIS reverse proxy):
```powershell
New-NetFirewallRule -DisplayName "Spring Boot 8080 (local only)" `
  -Direction Inbound -LocalPort 8080 -Protocol TCP `
  -RemoteAddress 127.0.0.1 -Action Allow

New-NetFirewallRule -DisplayName "HTTP (80) público" `
  -Direction Inbound -LocalPort 80 -Protocol TCP -Action Allow
```

### Notas

- `JWT_SECRET` no `.env` deve ter ≥256 bits (32 bytes), sem caracteres `#` ou `=`
- `MIGRATION_APP_BASE_URL` nas env vars deve ser o domínio público com IIS
- Porta 5432 nativa na VM (Docker local usa 15433)
- Attachments funcionam com URL pública via `GET /api/files/{id}` (FileController serve BLOB da BD)
