# Migration Plan: Zoho → Manatal

## Strategy: Incremental — test, validate, scale

Cada candidato passa por este ciclo:

```
ZOHO ──→ LOAD (Tasklet) ──→ PROCESS (Chunk) ──→ WRITE (Manatal) ──→ TAG (Zoho)
         status=PENDENTE      fetch Zoho+App+Interviews   POST candidate    add "Exported"
         + attachments        transform + package         POST notes        remove "PendenteMigracao"
                                                          POST attachments
                                                          POST resume
```

---

## Requisitos

- Ingestão via Zoho Recruit API (paginação, filtro por tag + data)
- Transformação desacoplada via `CandidateMapper`
- Exportação para Manatal (candidate + notes + custom_fields + attachments + resume)
- Rate limiting: Manatal (600ms throttle) + Zoho (daily cap com 3 thresholds)
- Notificações Slack/Email no fim do batch
- Rastreabilidade completa (`candidate_migration` + `migration_log`)
- Tags Zoho: `PendenteMigracao` → `Exported`

---

## Ambientes

| Perfil | Banco | Chunk | Spring Batch Schema | Uso |
|--------|-------|-------|-------------------|-----|
| `dev` | H2 em memória | 1 | `always` | Testes locais, mock data |
| `prod` | PostgreSQL (Docker) | 10–50 | `never` | Migração real |

---

## Arquitetura Spring Batch

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          candidateMigrationJob                               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────┐   ┌──────────────────────────┐                │
│  │  Step 1                  │   │  Step 2                  │                │
│  │  loadCandidatesStep      │──▶│  migrateCandidateStep    │                │
│  │  (Tasklet)               │   │  (Chunk, size=N)         │                │
│  │  - search Zoho by tag    │   └──────────────────────────┘                │
│  │  - insert PENDENTE       │            │                                  │
│  └──────────────────────────┘    ┌───────┴────────┐                         │
│                                  │                │                         │
│                             Processor          Writer                       │
│                    ┌────────────────────┐  ┌─────────────────────────┐      │
│                    │ 1. Fetch Zoho      │  │ 1. POST Manatal        │      │
│                    │ 2. Fetch App       │  │ 2. POST Note           │      │
│                    │ 3. Fetch Interviews│  │ 3. POST StructuredInfo │      │
│                    │ 4. Download Att    │  │ 4. POST LinkedIn       │      │
│                    │ 5. Save Bytes DB   │  │ 5. POST Attachments    │      │
│                    │ 6. Transform       │  │ 6. POST Resume         │      │
│                    └────────────────────┘  │ 7. Update Status       │      │
│                                            └─────────────────────────┘      │
│  ┌──────────────────────────┐                                                │
│  │  Step 3                  │                                                │
│  │  tagZohoStep             │                                                │
│  │  (Tasklet)               │                                                │
│  │  - add tag "Exported"    │                                                │
│  │  - remove "Pendente"     │                                                │
│  └──────────────────────────┘                                                │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Estrutura de Pacotes

```
com.migration
├── Application.java
├── config/
│   ├── BatchConfig.java                (jobLauncher, jobRepository)
│   ├── ManatalProperties.java
│   ├── ZohoProperties.java
│   ├── SecurityConfig.java             (JWT filter + CORS)
│   └── OpenApiConfig.java             (Swagger info + security scheme)
├── batch/
│   ├── migration/
│   │   ├── CandidateMigrationJobConfig.java
│   │   ├── LoadCandidatesTasklet.java
│   │   ├── CandidateMigrationProcessor.java
│   │   ├── CandidateMigrationWriter.java
│   │   ├── CandidateMigrationPackage.java
│   │   └── TagZohoTasklet.java
│   └── listener/
│       └── BatchJobListener.java       (notificações Slack + Email)
├── entity/
│   ├── CandidateMigration.java
│   ├── MigrationLog.java
│   ├── RawZohoData.java
│   └── StoredAttachment.java
├── repository/
│   ├── CandidateMigrationRepository.java
│   ├── MigrationLogRepository.java
│   ├── RawZohoDataRepository.java
│   └── StoredAttachmentRepository.java
├── controller/
│   ├── AuthController.java
│   ├── FileController.java
│   ├── ManatalController.java
│   ├── MigrationController.java
│   ├── ZohoController.java
│   └── BatchController.java
├── service/
│   ├── ManatalClientService.java       (REST client com throttle)
│   ├── ZohoClientService.java          (REST client com OAuth refresh)
│   ├── ZohoAuthService.java           (OAuth 2.0 token management)
│   ├── MigrationService.java          (lógica de migração single)
│   ├── FileStorageService.java        (store + serve attachment bytes)
│   └── NotificationService.java       (Slack + Email alerts)
├── model/
│   ├── ManatalCandidate.java
│   ├── ManatalAttachment.java
│   └── ManatalResume.java
├── dto/
│   ├── MigrationSummary.java
│   └── LoginRequest.java
├── transform/
│   └── CandidateMapper.java
├── report/
│   └── ReportService.java
├── security/
│   ├── JwtUtils.java
│   ├── JwtAuthenticationFilter.java
│   └── SecurityProperties.java
└── exception/
    ├── ApiException.java
    ├── ErrorResponse.java
    └── GlobalExceptionHandler.java     (10 exception types, JSON unificado)
```

---

## Database

### `candidate_migration`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | SERIAL PK | |
| `zoho_candidate_id` | VARCHAR | ID no Zoho |
| `application_id` | VARCHAR | ID da application no Zoho |
| `manatal_candidate_id` | VARCHAR | ID criado no Manatal (pós-migração) |
| `status` | VARCHAR | `PENDENTE` → `PROCESSANDO` → `SUCESSO` / `ERRO` |
| `error_message` | TEXT | Motivo do erro |
| `chunk_attempt` | INT | Tentativa actual (para retry) |
| `tagged_in_zoho` | BOOLEAN | Se já marcou "Exported" no Zoho |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### `migration_log`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | SERIAL PK | |
| `candidate_migration_id` | BIGINT FK | Referência ao candidato |
| `step` | VARCHAR | `LOAD`, `FETCH_ZOHO`, `DOWNLOAD`, `POST_MANATAL`, `POST_NOTE`, `POST_ATTACHMENT`, `TAG_ZOHO` |
| `status` | VARCHAR | `OK` / `ERRO` |
| `message` | TEXT | Detalhe do log |
| `duration_ms` | BIGINT | Tempo gasto no passo |
| `created_at` | TIMESTAMP | |

### `raw_zoho_data`

JSON bruto dos candidatos Zoho (pré-transformação).

### `stored_attachments`

Bytes dos attachments baixados do Zoho, servidos via `/api/files/{id}`.

---

## Fluxo Detalhado — Migração de 1 Candidato

```
1.  GET  /Candidates/{id}                      → JSON + Resume (attachment ID)
2.  GET  /Applications?candidate={id}          → descobre Application ID
3.  GET  /Interviews?candidate={id}            → fetch interview data
4.  GET  /Candidates/{id}/Attachments          → CV original
5.  GET  /Applications/{appId}/Attachments     → CV template
6.  GET  /Attachments/{id}                     → download CV (bytes)
7.  saveAttachment() x2                        → StoredAttachment no DB
8.  POST /candidates/                          → cria no Manatal
    Resposta: { "id": 12345 }                 ← MANATAL ID
9.  POST /candidates/12345/notes/              → nota com Candidate_Description_Summary
10. POST /candidates/12345/notes/              → structured info (expectedSalary, yearsofexperience, interview data)
11. POST /candidates/12345/notes/              → social media (LinkedIn)
12. POST /candidates/12345/attachments/        → attachment
13. POST /candidates/12345/resume/             → resume
```

---

## Manatal API — Estruturas

### POST /candidates/

```json
{
  "full_name": "João Silva",
  "email": "joao@email.com",
  "phone_number": "+351911111111",
  "country": "Portugal",
  "creator": 1193857,
  "owner": 1193857,
  "custom_fields": {
    "linkedin": "https://linkedin.com/in/joao",
    "skills": ["eMarketing", "Canva"],
    "csalary": 50000,
    "city": "Lisbon"
  }
}
```

### POST /candidates/{id}/attachments/

```json
{
  "name": "CV - João Silva",
  "description": "Curriculum Vitae",
  "file": "https://..."   ← URL pública
}
```

### POST /candidates/{id}/resume/

```json
{
  "resume_file": "https://..."   ← URL pública
}
```

### Activity (GET apenas — read-only)

```json
{
  "name": "Interview - Cargo",
  "description": "Detalhes",
  "activity_type": "interview"
}
```

> ⚠️ Activities **não aceitam POST** no Open API v3 do Manatal. Dados de entrevista vão para notas.

---

## Configurações por Perfil

### `application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:migration;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false

migration:
  batch:
    chunk-size: 1
```

### `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  batch:
    jdbc:
      initialize-schema: never
    job:
      enabled: false

migration:
  batch:
    chunk-size: 50
```

---

## Tratamento de Erros

| Situação | Ação |
|----------|------|
| Timeout / rede | Retry 3x (configurável) |
| Erro no processor | Skip + log em `migration_log` |
| Erro no writer | Rollback + status=`ERRO` |
| Candidate já migrado | Idempotência: verifica `manatal_candidate_id` |
| Rate limit Manatal (429) | Aguarda `rate-limit-ms` + retry |
| Zoho daily cap ≤5 | Halt — lança `ApiException`, interrompe batch |
| Zoho daily cap <20 | Pausa 60s até recuperar |
| Zoho daily cap <100 | Log warning + alerta |

Todas as excepções devolvem JSON estruturado via `ErrorResponse`:
- 400: validation, malformed request
- 401/403: authentication/authorization (JSON, não redirect)
- 404: recurso não encontrado
- 502: bad gateway (Zoho/Manatal API)
- 500: erro interno

---

## Rate Limiting

| API | Mecanismo | Configuração |
|---|---|---|
| Manatal | `throttle()` — `Thread.sleep` entre chamadas | `migration.manatal.rate-limit-ms: 600` |
| Zoho | Leitura do header `X-RATE-LIMIT-REMAINING` | 3 thresholds: warning (<100), critical (<20), halt (≤5) |

O Zoho não tem rate limit por segundo mas sim um **daily cap** de créditos (GET = 1 crédito). A app monitoriza o header em cada resposta e age conforme o threshold.

---

## Relatórios

### GET /api/batch/report

```json
{
  "total": 1000,
  "sucesso": 850,
  "erro": 50,
  "pendente": 100,
  "taxaSucesso": "85%",
  "ultimaExecucao": "2026-06-15T10:00:00",
  "errosPorTipo": {
    "POST_MANATAL": 20,
    "DOWNLOAD_ATTACHMENT": 15,
    "FETCH_ZOHO": 15
  },
  "topErros": [
    { "zohoId": "123", "step": "POST_MANATAL", "message": "Timeout" }
  ]
}
```

### Notificações (BatchJobListener)

- Slack: webhook com resumo formatado
- Email: relatório via SMTP
- Activado por `migration.alerts.enabled=true`

---

## Mock Data (Dev)

`data.sql` para H2:

```sql
INSERT INTO candidate_migration (zoho_candidate_id, status)
VALUES ('76333000000000001', 'PENDENTE');

INSERT INTO candidate_migration (zoho_candidate_id, status)
VALUES ('76333000000000002', 'PENDENTE');
```

---

## Testes

```powershell
.\mvnw.cmd test
```

| Teste | Cenários | Descrição |
|---|---|---|
| `CandidateMapperTest` | 30 | Transformação, linkedin, skills, custom_fields, structured info |
| `CandidateMigrationJobTest` | 3 | Batch job: sucesso, erro writer, erro processor |
| `H2ConsoleTest` | 1 | Integração com sleep para inspeção |
| `ApplicationTests` | 1 | Context load |

---

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/auth/login` | Obter token JWT |
| `GET` | `/api/zoho/candidates` | Lista 10 candidatos Zoho |
| `GET` | `/api/zoho/candidates/save` | Fetch + save raw JSON |
| `GET` | `/api/zoho/candidates/{id}` | Fetch 1 candidate by ID |
| `GET` | `/api/zoho/candidates/{id}/attachments` | List attachments |
| `GET` | `/api/zoho/attachments/{id}` | Download attachment |
| `GET` | `/api/zoho/candidates/{cid}/attachments/{aid}/save` | Store attachment in DB |
| `GET` | `/api/zoho/interviews` | Fetch one interview |
| `GET` | `/api/zoho/applications` | Fetch one application |
| `GET` | `/api/zoho/applications/{id}/attachments` | Application attachments |
| `GET` | `/api/zoho/tags` | List tags by module |
| `GET` | `/api/manatal/candidates` | Fetch first Manatal candidate |
| `GET` | `/api/manatal/candidates/{id}` | Fetch candidate by ID |
| `GET` | `/api/manatal/custom-fields` | Verify custom fields |
| `GET` | `/api/migration/candidates/{id}/preview` | Preview transformed data |
| `POST` | `/api/migration/candidates/{id}/migrate` | Migrate single candidate |
| `GET` | `/api/migration/custom-fields/verify` | Compare expected vs existing |
| `POST` | `/api/migration/candidates/{mid}/attachments/test` | Test attachment upload |
| `POST` | `/api/migration/candidates/{mid}/resume/test` | Test resume upload |
| `GET` | `/api/migration/tags/verify` | Verify Zoho tag exists |
| `POST` | `/api/batch/run` | Trigger batch job |
| `GET` | `/api/batch/report` | Migration dashboard |
| `GET` | `/api/batch/logs` | Audit logs |
| `GET` | `/api/files/{id}` | Serve attachment bytes |

---

## Mapeamento de Campos

### Zoho → Manatal (nativos)

| Zoho API | Manatal API |
|----------|-------------|
| `Full_Name` | `full_name` |
| `Email` | `email` |
| `Phone` | `phone_number` |
| `Country` | `candidate_location` |
| `Candidate_Description_Summary` + `Salary_Notes` | `description` |
| `Currency` | `ccurrency` |
| `Expected_Currency` | `ecurrency` |

### Zoho → Manatal (custom_fields)

| Campo | Tipo | Zoho |
|---|---|---|
| `canrelocate` | boolean | `Relocation` ("Yes"/"No") |
| `workvisaeucitizenship` | boolean | `WorkVisa` ("Yes"/"No") |
| `civilstatus` | string | `Civil_Status` |
| `availabilityweeks` | int | `Availability_Days / 7` |
| `numberofdependants` | int | `Number_of_Dependants` |
| `consent_to_rgpd` | string | `Consent_to_RGPD` |
| `additional_info` | string | `Additional_Information` |
| `csalary` | int | `Current_Salary` |
| `first_name` | string | `First_Name` |
| `last_name` | string | `Last_Name` |
| `city` | string | `City` |
| `salary_notes` | string | `Salary_Notes` |
| `linkedin` | string | `LinkedIn__s` |
| `skills` | array | `Stacks_LinkedIn` / `Skills` |

### Notas estruturadas (não custom fields)

Campos que **não existem como custom fields** no Manatal e são enviados como notas:
- `expectedsalary` — salário esperado
- `yearsofexperience` — anos de experiência
- Interview data — type, round, interviewer, status, rating, feedback

### Normalizações

- `name` → `full_name`
- `phonenumber` → `phone_number`
- `creator` / `owner` → integer ID fixo 1193857 (não string)

---

## Validações de Campos no Mapper

### LinkedIn

| Entrada (Zoho) | Saída (Manatal) | Regra |
|----------------|-----------------|-------|
| `"https://linkedin.com/in/fulano"` | igual | Mantém |
| `"linkedin.com/in/fulano"` | `"https://linkedin.com/in/fulano"` | Prefixa `https://` |
| `"fulano"` | `"https://fulano"` | Prefixa `https://` |
| `null` / vazio | `null` | Ignora |

### Skills

Skills do Zoho filtradas contra lista fixa de skills válidas no Manatal:

```
["eMarketing", "Broadcasting", "Consultancy Skills", "Paid Social",
 "Search Engine Optimisation", "eCommerce", "Strategy Development",
 "Email Marketing", "YouTube", "Blogging", "Google Ads", "Facebook",
 "Sales", "Sales and Marketing", "Screaming Frog", "Social Media", "Canva"]
```

| Entrada (Zoho) | Saída (Manatal) |
|----------------|-----------------|
| `["eMarketing", "Google Ads"]` | `["eMarketing", "Google Ads"]` |
| `["Python", "eMarketing", "Java"]` | `["eMarketing"]` |
| `"eMarketing\nPython\nCanva"` (multilinha) | `["eMarketing", "Canva"]` |
| `null` | `null` |

---

## Fases de Implementação

### Fase 1 — Infraestrutura ✅
- `pom.xml` + profiles yml + `@EnableBatchProcessing`
- Entidades + repositórios
- `BatchConfig.java`

### Fase 2 — Carga de IDs ✅
- `LoadCandidatesTasklet`
- `CandidateMigrationJobConfig`
- `BatchController`

### Fase 3 — Migração (core) ✅
- `CandidateMigrationProcessor`
- `CandidateMigrationWriter`
- `CandidateMigrationPackage`

### Fase 4 — Visibilidade ✅
- `BatchJobListener` + notificações
- `ReportService`
- `MigrationSummary`

### Fase 5 — Attachments ✅
- `FileStorageService`
- `ManatalAttachment` / `ManatalResume`

### Fase 6 — Idempotência & Tags ✅
- `TagZohoTasklet`: Step 3 do job
- Retry (`retry-limit: 3`)
- Tags `PendenteMigracao` → `Exported`

### Fase 7 — Resiliência ✅
- Rate limiting Manatal (throttle)
- Zoho daily cap monitor (3 thresholds)
- GlobalExceptionHandler + ErrorResponse (10 exception types)
- AuthenticationEntryPoint (JSON 401/403)

### Fase 8 — Documentação ✅
- Swagger/OpenAPI (springdoc-openapi 2.8.6)
- `OpenApiConfig` com security scheme
- `@Operation` + `@ApiResponse` + `@Schema` em todos os endpoints e modelos

---

## Observações

- Activities do Manatal **não podem ser criadas via API** (só GET) — entrevistas vão para notas
- Manatal espera **URL pública** para upload de ficheiros, não bytes
- `linkedin` e `skills` são custom fields no Manatal — vão dentro de `custom_fields`
- `expectedsalary` e `yearsofexperience` não existem como custom fields — vão para notas
- Owner/creator fixo como 1193857 (ID Helpdesk no Manatal)
- Dev usa H2 com `data.sql`, prod usa PostgreSQL
- Chunk size = 1 em dev para isolamento de falhas; prod pode ir até 50
