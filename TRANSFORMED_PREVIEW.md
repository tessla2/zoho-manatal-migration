# Transformed JSON Preview

Baseado no Zoho candidate **76333000000850000 — Mock Data**

## Preview do JSON transformado

```json
{
  "name": "Mock Data",
  "description": "Mock Description",
  "creator": "Mock Creator",
  "owner": "Mock Owner",
  "yearofexperience": null,
  "country": "Portugal",
  "availability": 0,
  "nationalities": "Portuguese",
  "number_of_dependents": null,
  "consent_to_rgpd_": "Pending",
  "aceitar_condi_es": null,
  "email": null,
  "phonenumber": "+351000000000",
  "ccurrency": "EUR",
  "ecurrency": "EUR",
  "worktype": null,
  "linkedin": null,
  "skills": [
    "Mock Skill 1",
    "Mock Skill 2"
  ],
  "note": [
    {
      "content": "Mock Data",
      "creator": "Mock Note Creator",
      "created_at": "2025-04-02T10:37:56+01:00"
    }
  ]
}
```

## Mapeamento aplicado

| Campo Manatal | Valor                  | Origem Zoho |
|--------------|------------------------|-------------|
| `name` | "Mock Data"            | `Full_Name` |
| `email` | `null` (vazio no Zoho) | `Email` |
| `phonenumber` | "+35100000000"         | `Phone` |
| `linkedin` | `null` (vazio no Zoho) | `LinkedIn__s` |
| `country` | "Portugal"             | `Country` |
| `nationalities` | "Portuguese"           | `Nationalities[0]` |
| `yearofexperience` | `null` (vazio no Zoho) | `Experience_in_Years` |
| `availability` | `0`                    | `Availability_Days` |
| `number_of_dependents` | `null` (vazio no Zoho) | `Number_of_Dependants` |
| `consent_to_rgpd_` | "Pending"              | `Consent_to_RGPD` |
| `ccurrency` / `ecurrency` | "EUR"                  | `Currency` |
| `creator` / `owner` | "Mock Data"            | `Candidate_Owner.name` |
| `description` | Summary + Salary Notes | `Candidate_Description_Summary` + `Salary_Notes` |
| `skills` | 8 skills parsed        | `Stacks_LinkedIn` (split by line breaks) |
| `worktype` | `null`                 | Não mapeado — sem campo correspondente no Zoho |
| `aceitar_condi_es` | `null`                 | Não mapeado |
| `note` | 1 note com summary     | `Candidate_Description_Summary` + `Created_By.name` |

## Para testar

```bash
# Ver o JSON transformado sem enviar ao Manatal
GET http://localhost:8080/api/migration/candidates/76333000000857739/preview

# Se estiver correto, migrar
GET http://localhost:8080/api/migration/candidates/76333000000857739/migrate
```
