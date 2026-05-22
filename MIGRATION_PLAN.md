# Migration Plan: Zoho → Manatal

## Strategy: Incremental — test, validate, scale

Cada candidato passa por este ciclo:

```
ZOHO ──→ RAW DATA (PG/H2) ──→ TRANSFORM ──→ MANATAL ──→ TAG (ZOHO)
         status=PENDENTE        manatal_id set  tag "Exported"
         or status=ERRO         + error_message
```

---

## Requisitos

- Ingestão via Zoho Recruit API (paginação padrão)
- Persistência RAW
- Transformação desacoplada
- Exportação para Manatal
- Rastreabilidade completa (saber o que foi processado e o que não foi)
- Futuro: retry/idempotência + write tag "Exported" no Zoho

---

## Ambientes

| Perfil | Banco | Chunk | Spring Batch Schema | Uso |
|--------|-------|-------|-------------------|-----|
| `dev` | H2 em memória | 1 | `always` | Testes locais, mock data |
| `prod` | PostgreSQL (Docker) | 10–50 | `never` | Migração real |

---

## Arquitetura Spring Batch

```
┌─────────────────────────────────────────────────────────────────────┐
│                     candidateMigrationJob                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────┐   ┌──────────────────────┐               │
│  │  Step 1              │   │  Step 2              │               │
│  │  loadCandidatesStep  │──▶│  migrateCandidateStep │               │
│  │  (Tasklet)           │   │  (Chunk, size=N)     │               │
│  └──────────────────────┘   └──────────────────────┘               │
│                                     │                              │
│                            ┌────────┴────────┐                    │
│                            │                 │                    │
│                       Processor           Writer                  │
│                  ┌──────────────────┐  ┌───────────────────┐      │
│                  │ 1. Fetch Zoho    │  │ 1. POST Manatal   │      │
│                  │ 2. Fetch App     │  │ 2. POST Attach    │      │
│                  │ 3. Download Att  │  │ 3. POST Resume    │      │
│                  │ 4. Save Bytes DB │  │ 4. Update Status  │      │
│                  │ 5. Transform     │  │ 5. Tag Zoho (fut) │      │
│                  └──────────────────┘  └───────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Estrutura de Pacotes

```
com.migration
├── Application.java                    (@EnableBatchProcessing)
├── config/
│   ├── BatchConfig.java                (jobLauncher, jobRepository)
│   ├── ManatalProperties.java          (já existe)
│   └── ZohoProperties.java             (já existe)
├── batch/
│   ├── migration/
│   │   ├── CandidateMigrationJobConfig.java   (job + steps)
│   │   ├── LoadCandidatesTasklet.java         (Step 1)
│   │   ├── CandidateMigrationProcessor.java   (Step 2 - process)
│   │   ├── CandidateMigrationWriter.java      (Step 2 - write)
│   │   └── CandidateMigrationPackage.java     (DTO entre steps)
│   └── listener/
│       ├── JobCompletionListener.java         (log resultado)
│       └── StepCompletionListener.java        (log cada step)
├── entity/
│   ├── CandidateMigration.java         (tabela de rastreio)
│   └── MigrationLog.java              (audit log detalhado)
├── repository/
│   ├── CandidateMigrationRepository.java
│   └── MigrationLogRepository.java
├── controller/
│   ├── ManatalController.java          (já existe)
│   ├── MigrationController.java        (já existe, será expandido)
│   ├── ZohoController.java             (já existe)
│   └── BatchController.java            (trigger job + reports)
├── service/
│   ├── ManatalClientService.java       (já existe, expandir)
│   ├── ZohoClientService.java          (já existe)
│   ├── ZohoAuthService.java            (já existe)
│   └── FileStorageService.java         (servir bytes para URL do Manatal)
├── model/
│   ├── ManatalCandidate.java           (já existe)
│   ├── ManatalAttachment.java          (novo)
│   ├── ManatalResume.java              (novo)
│   ├── StoredAttachment.java           (já existe)
│   └── ZohoAttachment.java             (já existe)
├── dto/
│   └── MigrationSummary.java           (dashboard status)
├── transform/
│   └── CandidateMapper.java            (já existe, expandir)
├── report/
│   └── ReportService.java              (gerar relatórios)
├── exception/
│   ├── ApiException.java               (já existe)
│   └── GlobalExceptionHandler.java     (já existe)
├── client/                             (vazio — futuros clients)
├── extract/                            (vazio — extractors)
├── loader/                             (vazio — loaders)
└── oauth/                              (vazio — futuros providers)
```

---

## Database

### Tabela nova: `candidate_migration`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | SERIAL PK | |
| `zoho_candidate_id` | VARCHAR | ID no Zoho |
| `application_id` | VARCHAR | ID da application no Zoho |
| `manatal_candidate_id` | VARCHAR | ID criado no Manatal (preenchido após POST) |
| `status` | VARCHAR | `PENDENTE` → `PROCESSANDO` → `SUCESSO` / `ERRO` |
| `error_message` | TEXT | Motivo do erro |
| `chunk_attempt` | INT | Tentativa atual (para retry) |
| `tagged_in_zoho` | BOOLEAN | Se já marcou "Exported" no Zoho |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Tabela nova: `migration_log`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | SERIAL PK | |
| `candidate_migration_id` | BIGINT FK | Referência ao candidato |
| `step` | VARCHAR | `LOAD`, `FETCH_ZOHO`, `DOWNLOAD`, `POST_MANATAL`, `POST_ATTACHMENT`, `TAG_ZOHO` |
| `status` | VARCHAR | `OK` / `ERRO` |
| `message` | TEXT | Detalhe do log |
| `duration_ms` | BIGINT | Tempo gasto no passo |
| `created_at` | TIMESTAMP | |

### Tabelas existentes

- `raw_zoho_data` — JSON bruto do Zoho (já existe)
- `stored_attachments` — bytes dos anexos baixados (já existe)

---

## Fluxo Detalhado — Migração de 1 Candidato

```
1. GET  /Candidates/{id}                 → JSON + Resume (attachment ID)
2. GET  /Applications?candidate={id}     → descobre Application ID
3. GET  /Applications/{appId}/Attachments → CV template
4. GET  /Candidates/{id}/Attachments     → CV original
5. GET  /Attachments/{id}                → download CV original (bytes)
6. GET  /Attachments/{id}                → download CV template (bytes)
7. saveAttachment() x2                   → StoredAttachment no DB
8. POST /candidates/                     → cria no Manatal
   Resposta: { "id": 12345 }            ← MANATAL ID
9. POST /candidates/12345/attachments/   → sobe CV template
   Payload: { "name":"CV Empresa", "file":"http://localhost:8080/api/files/1" }
10. POST /candidates/12345/resume/       → sobe CV original
    Payload: { "resume_file": "http://localhost:8080/api/files/2" }
```

---

## Manatal API — Estruturas

### POST /candidates/{candidate_pk}/attachments/

```json
{
  "name": "CV - Nome Candidato",
  "description": "CV com template da empresa",
  "file": "https://..."   ← URL, não binário
}
```

### POST /candidates/{candidate_pk}/resume/

```json
{
  "resume_file": "https://..."   ← URL (pdf, doc, docx, rtf)
}
```

### Activity (read-only, GET apenas)

```json
{
  "name": "Interview - Cargo",
  "description": "Detalhes",
  "activity_type": "interview",
  "due_date": "2025-01-14T10:00:00Z",
  "duration": 60,
  "importance": "normal",
  "is_done": true,
  "location": "Online"
}
```

> ⚠️ O endpoint de activities **não aceita POST** no Open API v3.

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
| Rate limit (429) | Aguarda `rate-limit-ms` + retry |

---

## Relatórios

### Endpoint de dashboard

```
GET /api/batch/report
```

```json
{
  "total": 1000,
  "sucesso": 850,
  "erro": 50,
  "pendente": 100,
  "taxa_sucesso": "85%",
  "ultima_execucao": "2026-05-20T10:00:00Z",
  "erros_por_tipo": {
    "POST_MANATAL": 20,
    "DOWNLOAD_ATTACHMENT": 15,
    "FETCH_ZOHO": 15
  },
  "top_erros": [
    { "zoho_id": "123", "step": "POST_MANATAL", "message": "Timeout" }
  ]
}
```

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

## Dependências Maven (novas)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>
```

---

## Fases de Implementação

### Fase 1 — Infraestrutura

| O quê | Arquivos |
|-------|----------|
| `pom.xml` + profiles yml + `@EnableBatchProcessing` | `pom.xml`, `application-dev.yml`, `application-prod.yml` |
| Entidades + repositórios | `CandidateMigration`, `MigrationLog`, + repositories |
| `BatchConfig.java` | Config do Spring Batch |

### Fase 2 — Carga de IDs

| O quê | Arquivos |
|-------|----------|
| `LoadCandidatesTasklet` | Lê mock ou query |
| `CandidateMigrationJobConfig` | Job + Step 1 |
| `BatchController` | Trigger `/api/batch/run` |

### Fase 3 — Migração (core)

| O quê | Arquivos |
|-------|----------|
| `CandidateMigrationProcessor` | Fetch Zoho + download + transform |
| `CandidateMigrationWriter` | POST Manatal + attachments + resume |
| `CandidateMigrationPackage` | DTO de dados entre steps |

### Fase 4 — Visibilidade

| O quê | Arquivos |
|-------|----------|
| `JobCompletionListener` | Log resultado |
| `StepCompletionListener` | Log cada step |
| `ReportService` | Dashboard `/api/batch/report` |

### Fase 5 — Attachments

| O quê | Arquivos |
|-------|----------|
| `FileStorageService` | Servir bytes para URL |
| `ManatalAttachment` / `ManatalResume` | Models |

### Fase 6 — Idempotência (futuro)

| O quê | Descrição |
|-------|-----------|
| Step 3: `tagZohoStep` | `POST /Candidates/{id}/add_tags?tags=Exported` |
| Retry | `retry-limit: 3` no step |

---

## API Endpoints (Após Implementação)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/zoho/candidates` | Fetch 10 candidates from Zoho |
| `GET` | `/api/zoho/candidates/{id}` | Fetch 1 candidate by ID |
| `GET` | `/api/zoho/candidates/save` | Fetch + save raw JSON to PG |
| `GET` | `/api/zoho/candidates/{id}/attachments` | List attachments |
| `GET` | `/api/zoho/attachments/{id}` | Download attachment binary |
| `GET` | `/api/zoho/candidates/{cid}/attachments/{aid}/save` | Download + store in PG |
| `GET` | `/manatal/candidates/{candidateId}/activities` | List activities |
| `GET` | `/api/migration/candidates/{id}/preview` | Preview transformed |
| `GET` | `/api/migration/candidates/{id}/migrate` | Migrate 1 candidate |
| `POST` | `/api/batch/run` | Trigger batch job |
| `GET` | `/api/batch/report` | Migration dashboard |
| `GET` | `/api/files/{id}` | Serve attachment bytes |

---

## Validações de Campos no Mapper

### LinkedIn (`linkedin` em custom_fields)

O Zoho envia o LinkedIn em vários formatos. O Manatal só aceita URLs que começam com `https://`.

| Entrada (Zoho) | Saída (Manatal) | Regra |
|----------------|-----------------|-------|
| `"https://linkedin.com/in/fulano"` | `"https://linkedin.com/in/fulano"` | Mantém |
| `"linkedin.com/in/fulano"` | `"https://linkedin.com/in/fulano"` | Prefixa `https://` |
| `"fulano"` | `"https://fulano"` | Prefixa `https://` |
| `null` / vazio | `null` | Ignora |

### Skills (`skills` em custom_fields)

Skills do Zoho (livres) precisam ser filtradas contra uma **lista fixa de skills válidas** no Manatal:

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
| `["Python", "Node"]` | `[]` |
| `"eMarketing\nPython\nCanva"` (multilinha) | `["eMarketing", "Canva"]` |
| `null` | `null` |

### Mapeamento de nomes de campos

| Zoho API | Manatal API (campo padrão) | Manatal API (custom_fields) |
|----------|---------------------------|----------------------------|
| `Full_Name` | `full_name` | — |
| `Email` | `email` | — |
| `Phone` | `phone_number` | — |
| `LinkedIn__s` | — | `linkedin` |
| `Skills` / `Stacks_LinkedIn` | — | `skills` |
| `Country` | — | `country` |
| `Candidate_Owner` | `owner` (integer ID) | — |

> ⚠️ `linkedin` e `skills` devem ser enviados dentro de `custom_fields` no POST do Manatal, pois não fazem parte do schema padrão.

### Normalizações
- `name` → `full_name`
- `phonenumber` → `phone_number`
- `creator` / `owner` → integer ID (não string name)

---

## Testes Unitários

### O que testar no `CandidateMapper`

| Teste | Cenário | Valida |
|-------|---------|--------|
| `linkedinComHttps` | Zoho envia `"https://..."` | Mantém igual |
| `linkedinSemProtocolo` | Zoho envia `"linkedin.com/in/x"` | Prefixa `https://` |
| `linkedinApenasUsuario` | Zoho envia `"fulano"` | Prefixa `https://fulano` |
| `linkedinNulo` | Zoho envia `null` | `linkedin = null` |
| `linkedinVazio` | Zoho envia `""` | `linkedin = null` |
| `skillsTodasValidas` | `["eMarketing", "Canva"]` | Mantém ambas |
| `skillsMisturadas` | `["Python", "eMarketing", "Java"]` | Só `["eMarketing"]` |
| `skillsNenhumaValida` | `["Python", "Node"]` | `[]` |
| `skillsMultilinha` | `"eMarketing\nPython\nCanva"` | `["eMarketing", "Canva"]` |
| `skillsNulo` | `null` | `null` |
| `skillsArrayVazio` | `[]` | `[]` |
| `fullNameMapeado` | `Full_Name: "João"` | `full_name = "João"` |
| `emailMapeado` | `Email: "a@b.com"` | `email = "a@b.com"` |
| `phoneMapeado` | `Phone: "11999999999"` | `phone_number = "11999999999"` |
| `descricaoCompleta` | Summary + Salary | Concatena com `\n\n` |
| `descricaoApenasSummary` | Só Summary | Só o summary |
| `ownerExtraido` | `Candidate_Owner: {name: "João"}` | `ownerName = "João"` |

### O que testar no `ManatalClientService`

| Teste | Cenário | Valida |
|-------|---------|--------|
| `createCandidateSucesso` | Manatal retorna 201 com ID | Retorna o body |
| `createCandidateErro` | Manatal retorna 400 | Lança `ApiException` |
| `createCandidateTimeout` | Timeout de conexão | Lança `ApiException.badGateway` |
| `fetchActivitiesSucesso` | Manatal retorna 200 com array | Retorna JSON |
| `fetchActivitiesVazio` | Manatal retorna 200 com `[]` | Retorna `[]` |

### Estrutura de testes

```
src/test/java/com/migration/
├── ApplicationTests.java                      (já existe)
├── transform/
│   └── CandidateMapperTest.java               (17+ cenários)
├── service/
│   ├── ManatalClientServiceTest.java          (3+ cenários)
│   └── ZohoClientServiceTest.java             (futuro)
└── batch/
    └── migration/
        ├── LoadCandidatesTaskletTest.java     (futuro)
        ├── CandidateMigrationProcessorTest.java (futuro)
        └── CandidateMigrationWriterTest.java   (futuro)
```

---

## Observações

- Activities do Manatal **não podem ser criadas via API** (só GET)
- Manatal espera **URL** para upload de arquivos, não bytes
- O elo entre Zoho e Manatal é o `zoho_candidate_id` no `StoredAttachment`
- Dev usa H2 com `data.sql`, prod usa PostgreSQL com schema gerenciado
- `linkedin` e `skills` são custom fields no Manatal — vão dentro de `custom_fields`
