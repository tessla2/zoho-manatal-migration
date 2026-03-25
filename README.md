# ATS Transfer Batch

Batch application responsible for **transferring data between ATS systems**, with processing control, automatic retry, reprocessing, and observability.

---

## Overview

This project uses **Spring Batch** to process data asynchronously and resiliently, ensuring:

* Transfer status tracking
* Automatic retry on temporary failures
* Manual reprocessing of failed records
* CSV data export
* Monitoring via logs and Slack alerts

---

## Architecture

```
[ Scheduler / Trigger ]
          ↓
[ Spring Batch Job ]
          ↓
[ Reader → Processor → Writer ]
          ↓
[ PostgreSQL ]
          ↓
[ Internal API + CSV Export + Reprocessing ]
```

---

## Technology Stack

**Backend**

* Java 21
* Spring Boot 4
* Spring Batch

**Persistence**

* PostgreSQL (dev and production)
* H2 (for quick tests)
* Spring Data JPA / Hibernate

**Resilience**

* Spring Batch Fault Tolerance (retry + skip)
* Resilience4j (external integrations)

**Observability**

* SLF4J + Logback
* Alerts via Slack Webhook

**Other Tools**

* Apache Commons CSV
* Docker
* GitHub Actions (CI/CD)

---

## Processing Flow

1. Records are saved with status `PENDING`.
2. The batch processes data in chunks.
3. Each item goes through:

   * Validation (Processor)
   * Sending to the target ATS (Writer)
4. Outcome:

   * `SUCCESS` → completed
   * `FAILED` → available for reprocessing

---

## Status Model

| Status     | Description            |
| ---------- | ---------------------- |
| PENDING    | Waiting for processing |
| PROCESSING | In progress            |
| SUCCESS    | Processed successfully |
| FAILED     | Processing failed      |

---

## Retry and Reprocessing

* Automatic retry per item (configurable)
* Skip failed items after retry limit
* Reprocessing via internal API

---

## Internal Endpoints

| Endpoint                    | Description              |
| --------------------------- | ------------------------ |
| `POST /transfer/reprocess`  | Reprocess failed records |
| `GET /transfer/{id}/status` | Check status             |
| `GET /transfer/export`      | Export data to CSV       |

---

## Environments

| Environment | Database            |
| ----------- | ------------------- |
| dev         | PostgreSQL (Docker) |
| test        | H2 (in-memory)      |
| prod        | PostgreSQL          |

---

## Running with Docker

```bash
docker build -t ats-transfer-batch .
docker run -p 8080:8080 ats-transfer-batch
```

---

## CI/CD

Pipeline with GitHub Actions:

* Build the application
* Run automated tests (H2)
* Build Docker image
* Push to registry
* Send alerts on failure

---

## Project Structure

```
src/
 ├── batch/        # Jobs, Steps, Configurations
 ├── domain/       # Entities and business rules
 ├── repository/   # Database access
 ├── service/      # Application logic
 ├── controller/   # Internal API
 └── config/       # General configurations
```

---

## Alerts

Critical failures trigger notifications via **Slack Webhook**.

---

## Best Practices

* Idempotent processing
* Status tracking per record
* Separation between technical logs and business logs
* Controlled retries to avoid infinite loops
* Observability and traceability

---

## Author

Project developed for robust and scalable ATS integrations.
