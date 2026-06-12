# Zoho → Manatal Migration

Migração de candidatos do Zoho Recruit para o Manatal.

## Stack

- Java 21, Spring Boot 3.x, Spring Batch
- JPA / H2 (dev) / PostgreSQL (prod)
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
OpenAPI JSON: `http://localhost:8080/api-docs`

## Docker

```bash
# Dev (H2 em memória)
cd docker
docker compose up

# Prod (com Postgres)
docker compose --profile prod up
```

O Dockerfile usa **multi-stage build**:
- **builder**: compila com Maven + JDK 21
- **final**: imagem leve só com JRE 21 + JAR (~150MB)

Em prod, cria um ficheiro `docker/.env` baseado no `.env.example` com todas as variáveis.

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
| GET | `/candidates/{id}` | Busca candidato no Zoho |
| GET | `/candidates/{id}/attachments` | Anexos do candidato |
| GET | `/candidates/{id}/applications` | Applications do candidato |
| GET | `/applications/{id}/attachments` | Anexos da application |
| GET | `/candidates/{id}/interviews` | Entrevistas do candidato |

### Manatal (`/api/manatal`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/candidates` | Lista candidatos |
| GET | `/candidates/{id}` | Busca candidato por ID |

### Migration (`/api/migration`)
| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/candidates/{zohoId}/preview` | Preview dos dados mapeados |
| POST | `/candidates/{zohoId}/migrate` | Migra candidato único |
| GET | `/custom-fields/verify` | Verifica custom fields no Manatal |
| POST | `/candidates/{manatalId}/attachments/test?fileUrl=` | Testa attachment |
| POST | `/candidates/{manatalId}/resume/test?fileUrl=` | Testa resume |

### Batch (`/api/batch`)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/run` | Executa batch completo |
| GET | `/report` | Resumo das migrações |
| GET | `/logs` | Logs de auditoria |

## Fluxo Batch

```
LoadCandidatesStep → MigrateCandidateStep → TagZohoStep
```

1. **LoadCandidatesStep**: Busca candidatos no Zoho com tag `PendenteMigracao`, insere como `PENDENTE` no banco
2. **MigrateCandidateStep**: Processa registos `PENDENTE` — cria candidato, notas e attachments no Manatal
3. **TagZohoStep**: Adiciona tag `Exported` no Zoho e remove `PendenteMigracao`

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
- **Skip** (10): excepções inesperadas — se o limite for atingido o step falha
- **Não skip**: `ApiException` (queremos falhar para debug) e `NullPointerException`

### Rate Limiting

| API | Mecanismo | Config |
|---|---|---|
| Manatal | `throttle()` — `Thread.sleep` entre chamadas | `migration.manatal.rate-limit-ms: 600` |
| Zoho | Leitura de `X-RATE-LIMIT-REMAINING` nas respostas | `migration.zoho.rate-limit-threshold: 100` |

Zoho loga `warn` quando o limite restante fica abaixo do threshold. Disponível via `GET /api/batch/report` ou `getZohoRateLimitRemaining()`.

## Mapeamento Zoho → Manatal

### Campos nativos
| Manatal | Zoho |
|---|---|
| `full_name` | `Full_Name` |
| `email` | `Email` |
| `phone_number` | `Phone` |
| `candidate_location` | `Country` |
| `description` | `Candidate_Description_Summary` + `Salary_Notes` |
| `linkedin` | `LinkedIn__s` (custom_field) |
| `skills` | `Stacks_LinkedIn` ou `Skills` (custom_field, array) |
| `creator` / `owner` | `1193857` (fixo) |

### Custom fields
| Campo | Tipo | Zoho |
|---|---|---|
| `canrelocate` | boolean | `Relocation` |
| `workvisaeucitizenship` | boolean | `WorkVisa` |
| `civilstatus` | string | `Civil_Status` |
| `availabilityweeks` | int | `Availability_Days / 7` |
| `numberofdependants` | int | `Number_of_Dependants` |
| `consent_to_rgpd` | string | `Consent_to_RGPD` |
| `additional_info` | string | `Additional_Information` |
| `csalary` | int | `Current_Salary` |
| `first_name` | string | `First_Name` |
| `last_name` | string | `Last_Name` |
| `city` | string | `City` |
| `salary_notes` | string | `Salary_Notes` + `Expected_Salary` |
| `linkedin` | string | `LinkedIn__s` |
| `skills` | array | `Stacks_LinkedIn` / `Skills` |

### Notas estruturadas
- `expectedsalary` e `yearsofexperience` vão em notas (não existem como custom fields no Manatal)
- Dados de entrevistas (type, round, interviewer, status, rating, feedback) vão em notas estruturadas

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
  manatal:
    base-url: https://api.manatal.com/open/v3/
    rate-limit-ms: 600
  zoho:
    rate-limit-threshold: ${ZOHO_RATE_LIMIT_THRESHOLD:100}
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

## Notificações

No fim de cada execução do batch, a app envia um relatório automático se `migration.alerts.enabled=true`.

### Slack
1. Cria uma workspace em `slack.com`
2. Vai a `https://api.slack.com/apps` → **Create New App** → **From scratch**
3. Activa **Incoming Webhooks** → **Add New Webhook to Workspace**
4. Copia a URL e define como `SLACK_WEBHOOK_URL` no `.env`

### Email
Define as variáveis `SMTP_HOST`, `SMTP_USER`, `SMTP_PASS` no `.env` e `ALERT_EMAIL_TO` com o destinatário.

### Exemplo de relatório
```
📊 Relatório de Migração
Total: 50
✅ Sucesso: 48
❌ Erro: 2
⏳ Pendente: 0
Taxa de Sucesso: 96.0%
```

## Limitações

- Attachments exigem URL pública (ngrok / S3 / VPS)
- Sem endpoint de paragem do batch (`POST /api/batch/stop`)
- Zoho daily cap: apenas loga `warn` — não pausa automaticamente o batch
