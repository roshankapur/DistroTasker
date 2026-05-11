# Distro Tasker — Product Requirements Document

> **Version:** 1.0  
> **Author:** Systems Architecture Team  
> **Date:** May 2026  
> **Status:** Draft — Awaiting Developer Review  
> **Timeline:** 4–6 Weeks (Solo Developer)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Target Users](#3-target-users)
4. [Product Vision](#4-product-vision)
5. [Core Features (MVP)](#5-core-features-mvp)
6. [System Architecture](#6-system-architecture)
7. [Distributed Systems Considerations](#7-distributed-systems-considerations)
8. [Token Bucket Design](#8-token-bucket-design)
9. [Database Design](#9-database-design)
10. [API Design](#10-api-design)
11. [Execution Flow](#11-execution-flow)
12. [Reliability + Fault Tolerance](#12-reliability--fault-tolerance)
13. [Observability](#13-observability)
14. [Security Considerations](#14-security-considerations)
15. [MVP Scope vs Future Scope](#15-mvp-scope-vs-future-scope)
16. [Development Roadmap](#16-development-roadmap)
17. [Technical Risks](#17-technical-risks)
18. [Testing Strategy](#18-testing-strategy)
19. [Demo Scenarios](#19-demo-scenarios)
20. [Resume/Portfolio Positioning](#20-resumeportfolio-positioning)

---

## 1. Executive Summary

**Distro Tasker** is a distributed task scheduling engine that accepts user-defined jobs (shell commands, scripts, HTTP calls), persists them, and executes them reliably at a specified future time across a pool of concurrent worker threads — all while enforcing system-wide rate limiting via a Token Bucket algorithm.

### Why It Exists

Modern backend systems routinely need deferred execution: sending emails at a future time, retrying failed webhooks, running nightly data pipelines, or throttling API calls to downstream services. Building a scheduler that handles these correctly — with concurrency, fault tolerance, and rate control — is a non-trivial distributed systems problem.

### What Problem It Solves

| Problem | How Distro Tasker Addresses It |
|---|---|
| Jobs need to run at a future time | Persistent scheduling with polling-based dispatch |
| Multiple jobs arrive simultaneously | Concurrent worker pool with thread-safe execution |
| Downstream services can't handle burst traffic | Token Bucket rate limiter throttles execution throughput |
| Workers crash mid-execution | Heartbeat monitoring + automatic job reassignment |
| Jobs fail transiently | Configurable retry with exponential backoff |
| No visibility into what ran and when | Execution audit trail, metrics, structured logging |

### Why This Project Is Technically Impressive

This project compresses **six real-world distributed systems concepts** into a single, demoable application:

1. **Concurrent task execution** — thread pool management, race condition prevention
2. **Distributed scheduling** — clock-aware job dispatch with consistency guarantees
3. **Rate limiting** — Token Bucket algorithm with configurable refill rates
4. **Fault tolerance** — crash recovery, retries, dead-letter handling
5. **API design** — clean RESTful interface for job lifecycle management
6. **Observability** — structured logging, metrics, execution audit trails

This is not a CRUD app with a scheduler bolted on. It's a systems engineering project that demonstrates the kind of thinking required to build production infrastructure.

---

## 2. Problem Statement

### 2.1 The Scheduling Problem

Scheduling jobs for future execution sounds simple — store a timestamp, poll for due jobs, execute them. In practice, it breaks down immediately:

- **Clock drift**: If workers disagree on "now," jobs execute at the wrong time or are picked up twice.
- **Concurrent pickup**: Two workers poll at the same instant and both grab the same job.
- **Backpressure**: 10,000 jobs become due at midnight, overwhelming the database and downstream APIs.
- **Partial failure**: A worker picks up a job, starts executing, then crashes. The job is now stuck in `RUNNING` forever.
- **Ordering**: Jobs scheduled for the same second need deterministic execution order.

A correct scheduler must solve all of these simultaneously.

### 2.2 Why Rate Limiting Matters

Without rate limiting, a burst of due jobs (e.g., after a backlog clears) can:

- Saturate thread pools, starving other jobs
- Overwhelm downstream HTTP services with concurrent requests
- Trigger cascading failures in dependent systems
- Exhaust database connection pools

Rate limiting transforms an unpredictable burst workload into a smooth, controlled throughput — which is exactly what production systems need.

### 2.3 Why Distributed Execution Matters

Single-threaded execution has a hard ceiling: one job at a time, zero fault tolerance. If the process dies, everything stops.

Distributed execution (even across threads within a single JVM, which is our MVP scope) provides:

- **Parallelism**: Multiple jobs execute simultaneously
- **Isolation**: One slow/failing job doesn't block others
- **Resilience**: Other workers continue if one thread crashes
- **Scalability foundation**: The architecture can later extend to multi-node without rewriting

---

## 3. Target Users

| User Persona | Use Case | What They Care About |
|---|---|---|
| **Backend Engineers** | Schedule deferred tasks (emails, webhooks, cleanup jobs) | Reliability, API ergonomics, retry semantics |
| **Infrastructure Teams** | Internal job orchestration, cron replacement | Observability, rate control, failure handling |
| **Systems Design Learners** | Study distributed systems concepts in a real codebase | Clean architecture, well-documented design decisions |
| **Portfolio Reviewers** | Evaluate engineering depth and systems thinking | Concurrency handling, fault tolerance, rate limiting |

### Primary User for MVP

The **solo developer building the project**. The MVP must be demoable locally with clear, scriptable scenarios that showcase every core concept. The API should be clean enough that a reviewer can understand the system by reading the endpoints and watching a demo.

---

## 4. Product Vision

### 4.1 MVP Vision (Weeks 1–6)

A single-JVM, multi-threaded task scheduler that:

- Accepts jobs via REST API with a future execution time
- Persists jobs in PostgreSQL
- Dispatches due jobs to a configurable thread pool
- Enforces rate limiting via Token Bucket before execution
- Retries failed jobs with exponential backoff
- Tracks full execution history
- Exposes health, metrics, and job status endpoints
- Runs entirely via `docker-compose up`

### 4.2 Long-Term Vision (Post-MVP)

- Multi-node worker coordination via Redis-based distributed locking
- Cron/recurring job support
- Priority queues with job weighting
- Web dashboard for job monitoring
- Webhook notifications on job completion/failure
- Plugin architecture for custom executors (HTTP, gRPC, Lambda)

### 4.3 Explicit Non-Goals for MVP

| Non-Goal | Rationale |
|---|---|
| Kubernetes deployment | Adds infrastructure complexity with no learning value at MVP |
| Microservices architecture | A monolith with clean boundaries is faster to build and easier to demo |
| Multi-node clustering | Thread-based concurrency demonstrates the same concepts with less ops overhead |
| Web UI dashboard | REST API + logs are sufficient for demo; a UI adds weeks of unrelated work |
| User authentication system | MVP is single-tenant; auth is orthogonal to the core problem |
| Recurring/cron jobs | One-shot scheduling is complex enough; cron adds parsing and state management |

---

## 5. Core Features (MVP)

### Feature 1: Job Submission API

| Attribute | Detail |
|---|---|
| **Description** | REST endpoint to create a job with a command, execution time, and optional config (retries, timeout, priority) |
| **User Story** | *As a developer, I want to submit a job via API so that it runs automatically at a future time without me polling or waiting.* |
| **Technical Complexity** | Low — standard Spring Boot controller + validation |
| **Priority** | P0 — Critical |
| **Why MVP** | This is the entry point to the entire system. Without job submission, nothing else works. |

**Acceptance Criteria:**
- `POST /api/v1/jobs` accepts a JSON payload with command, `scheduledAt` timestamp, and optional retry/timeout config
- Returns `201 Created` with job ID and status `PENDING`
- Validates that `scheduledAt` is in the future
- Persists job to PostgreSQL immediately

---

### Feature 2: Scheduled Job Dispatcher

| Attribute | Detail |
|---|---|
| **Description** | Background poller that queries the database for jobs whose `scheduledAt` has passed, claims them atomically, and submits them to the worker pool |
| **User Story** | *As the system, I need to automatically detect due jobs and dispatch them to workers so that no human intervention is required after submission.* |
| **Technical Complexity** | **High** — requires atomic claiming (prevents duplicate pickup), efficient polling, and backpressure awareness |
| **Priority** | P0 — Critical |
| **Why MVP** | The dispatcher is the core scheduling engine. It's the most interesting distributed systems component. |

**Acceptance Criteria:**
- Polls database every N seconds (configurable, default 5s)
- Uses `UPDATE ... WHERE status = 'PENDING' AND scheduledAt <= NOW()` with row-level locking to atomically claim jobs
- Respects worker pool capacity — does not dispatch more jobs than available threads
- Assigns a `workerId` to each claimed job for traceability

---

### Feature 3: Concurrent Worker Pool

| Attribute | Detail |
|---|---|
| **Description** | A fixed-size thread pool (`ExecutorService`) that executes dispatched jobs concurrently, with per-job timeout enforcement |
| **User Story** | *As the system, I need to execute multiple jobs in parallel so that one slow job doesn't block others.* |
| **Technical Complexity** | Medium — thread pool management, timeout handling via `Future.get(timeout)`, graceful shutdown |
| **Priority** | P0 — Critical |
| **Why MVP** | Concurrency is a headline feature. A single-threaded scheduler would not demonstrate distributed systems thinking. |

**Acceptance Criteria:**
- Configurable pool size (default: 4 workers)
- Each job executes in its own thread with a configurable timeout
- Timed-out jobs are marked `FAILED` with reason `TIMEOUT`
- Worker threads are named for log traceability (e.g., `worker-1`, `worker-2`)
- Pool supports graceful shutdown — completes in-flight jobs on SIGTERM

---

### Feature 4: Token Bucket Rate Limiter

| Attribute | Detail |
|---|---|
| **Description** | A Token Bucket rate limiter that gates job execution. Before a job runs, it must acquire a token. If no tokens are available, the job waits or is re-queued. |
| **User Story** | *As an operator, I want to control how many jobs execute per second so that downstream services aren't overwhelmed.* |
| **Technical Complexity** | **High** — thread-safe token management, refill scheduling, integration with dispatcher, edge case handling (burst, starvation) |
| **Priority** | P0 — Critical |
| **Why MVP** | Rate limiting is a core differentiator. It transforms a basic scheduler into a production-grade system. |

**Acceptance Criteria:**
- Configurable capacity (max tokens) and refill rate (tokens/second)
- Thread-safe implementation using `AtomicLong` or `synchronized`
- Token check happens *before* job execution, not after dispatch
- Jobs that can't acquire a token are re-queued with a short delay (not dropped)
- Metrics: tokens available, tokens consumed, jobs throttled

---

### Feature 5: Retry with Exponential Backoff

| Attribute | Detail |
|---|---|
| **Description** | Failed jobs are automatically retried up to a configurable limit, with exponentially increasing delays between attempts |
| **User Story** | *As a developer, I want failed jobs to retry automatically so that transient failures don't require manual intervention.* |
| **Technical Complexity** | Medium — retry count tracking, backoff calculation, re-scheduling logic, max-retry detection |
| **Priority** | P0 — Critical |
| **Why MVP** | Retry logic is fundamental to reliable systems. It demonstrates understanding of failure modes and recovery strategies. |

**Acceptance Criteria:**
- Default max retries: 3 (configurable per job)
- Backoff formula: `delay = baseDelay * 2^(attemptNumber - 1)` (e.g., 1s, 2s, 4s)
- After max retries, job moves to `DEAD_LETTER` status
- Each retry creates a new execution history record
- Retry delay is enforced by updating `scheduledAt` to `NOW() + backoffDelay`

---

### Feature 6: Execution History & Audit Trail

| Attribute | Detail |
|---|---|
| **Description** | Every job execution attempt is logged to a `job_executions` table with timestamps, worker ID, status, output/error, and duration |
| **User Story** | *As an operator, I want to see the full execution history of every job so that I can diagnose failures and verify correct behavior.* |
| **Technical Complexity** | Low — standard database writes with structured data |
| **Priority** | P1 — High |
| **Why MVP** | Without execution history, the system is a black box. This is essential for demos and debugging. |

**Acceptance Criteria:**
- Records: job ID, worker ID, attempt number, start time, end time, duration, status, stdout/stderr (truncated to 10KB)
- Queryable via `GET /api/v1/jobs/{id}/executions`
- Immutable — execution records are never updated, only inserted

---

### Feature 7: Job Lifecycle Management API

| Attribute | Detail |
|---|---|
| **Description** | REST endpoints to query, cancel, and retry jobs |
| **User Story** | *As a developer, I want to check job status, cancel pending jobs, and manually retry failed ones via API.* |
| **Technical Complexity** | Low-Medium — state machine validation (can't cancel a completed job), concurrency-safe cancellation |
| **Priority** | P1 — High |
| **Why MVP** | A scheduler without lifecycle management is incomplete. These endpoints make the system interactive and demoable. |

**Acceptance Criteria:**
- `GET /api/v1/jobs` — list jobs with filtering (status, date range) and pagination
- `GET /api/v1/jobs/{id}` — get job details including current status and execution count
- `DELETE /api/v1/jobs/{id}` — cancel a `PENDING` job (returns 409 if already running/completed)
- `POST /api/v1/jobs/{id}/retry` — manually retry a `FAILED` or `DEAD_LETTER` job

---

### Feature 8: Health & Metrics Endpoints

| Attribute | Detail |
|---|---|
| **Description** | Actuator-style endpoints exposing system health, worker pool status, rate limiter state, and job statistics |
| **User Story** | *As an operator, I want to monitor the system's health and throughput at a glance.* |
| **Technical Complexity** | Low — Spring Boot Actuator + custom metrics beans |
| **Priority** | P1 — High |
| **Why MVP** | Observability is a key differentiator. Metrics endpoints demonstrate production-readiness thinking. |

**Acceptance Criteria:**
- `GET /api/v1/health` — system health, DB connectivity, worker pool status
- `GET /api/v1/metrics` — jobs created/completed/failed counts, rate limiter stats, avg execution time
- Metrics are computed in-memory (counters) and refreshed on read — no external metrics stack required for MVP

---

## 6. System Architecture

### 6.1 Architecture Overview

Distro Tasker is a **modular monolith** — a single Spring Boot application with clearly separated internal components. This avoids the operational overhead of microservices while maintaining clean architectural boundaries.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DISTRO TASKER (Single JVM)                   │
│                                                                     │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │   REST API    │───▶│   Job Service     │───▶│   PostgreSQL     │  │
│  │  Controller   │    │  (Business Logic) │    │   (Persistence)  │  │
│  └──────────────┘    └────────┬─────────┘    └──────────────────┘  │
│                               │                        ▲            │
│                               ▼                        │            │
│  ┌──────────────────────────────────────┐              │            │
│  │          Job Dispatcher              │              │            │
│  │  (Scheduled Poller — @Scheduled)     │──────────────┘            │
│  │  - Queries due jobs                  │                           │
│  │  - Claims atomically via UPDATE      │                           │
│  │  - Submits to worker pool            │                           │
│  └──────────────┬───────────────────────┘                           │
│                 │                                                    │
│                 ▼                                                    │
│  ┌──────────────────────────┐    ┌──────────────────────┐          │
│  │    Token Bucket Rate     │    │    Worker Pool        │          │
│  │    Limiter               │───▶│  (ExecutorService)    │          │
│  │  - tryConsume() gate     │    │  - Fixed thread pool  │          │
│  │  - Refill thread         │    │  - Per-job timeout    │          │
│  └──────────────────────────┘    │  - Graceful shutdown  │          │
│                                  └──────────┬───────────┘          │
│                                             │                       │
│                                             ▼                       │
│                                  ┌──────────────────────┐          │
│                                  │   Job Executor        │          │
│                                  │  - Runs command       │          │
│                                  │  - Captures output    │          │
│                                  │  - Writes exec record │          │
│                                  │  - Handles retries    │          │
│                                  └──────────────────────┘          │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Observability Layer                        │  │
│  │  - Structured Logging (SLF4J + Logback)                      │  │
│  │  - In-memory Metrics (AtomicLong counters)                   │  │
│  │  - Health endpoint                                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Component Responsibilities

| Component | Responsibility | Key Classes |
|---|---|---|
| **REST API Layer** | Accept job CRUD requests, validate input, return responses | `JobController`, `MetricsController` |
| **Job Service** | Business logic for job creation, cancellation, retry, status transitions | `JobService` |
| **Job Repository** | Database access, atomic claiming queries, execution history writes | `JobRepository`, `ExecutionRepository` |
| **Job Dispatcher** | Periodic poller that finds due jobs, claims them, and submits to worker pool | `JobDispatcher` |
| **Token Bucket** | Rate limiting gate — controls how many jobs can execute per time window | `TokenBucketRateLimiter` |
| **Worker Pool** | Thread pool that executes jobs concurrently with timeout enforcement | `WorkerPool` |
| **Job Executor** | Actual job execution logic — runs commands, captures output, handles results | `JobExecutor` |
| **Metrics Collector** | In-memory counters for job stats, rate limiter stats, pool utilization | `MetricsCollector` |

### 6.3 Data Flow

```
Client POST /api/v1/jobs
        │
        ▼
   ┌─────────┐     ┌───────────┐     ┌────────────┐
   │ Validate │────▶│  Persist  │────▶│  Return    │
   │  Input   │     │  to DB    │     │  201 + ID  │
   └─────────┘     │ status=   │     └────────────┘
                    │ PENDING   │
                    └─────┬─────┘
                          │
              (Asynchronous — poller runs every 5s)
                          │
                          ▼
                   ┌──────────────┐
                   │  Dispatcher  │
                   │  finds due   │
                   │  jobs via    │
                   │  SELECT...   │
                   │  FOR UPDATE  │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐     ┌──────────────┐
                   │  Claim job   │────▶│  Submit to   │
                   │  atomically  │     │  Worker Pool │
                   │  status=     │     └──────┬───────┘
                   │  DISPATCHED  │            │
                   └──────────────┘            ▼
                                        ┌──────────────┐
                                        │ Token Bucket │
                                        │ tryConsume() │
                                        └──────┬───────┘
                                               │
                                    ┌──────────┴──────────┐
                                    │                     │
                               Token OK             No Token
                                    │                     │
                                    ▼                     ▼
                             ┌────────────┐       ┌──────────────┐
                             │  Execute   │       │  Re-queue    │
                             │  Command   │       │  with delay  │
                             │  status=   │       │  status=     │
                             │  RUNNING   │       │  PENDING     │
                             └─────┬──────┘       └──────────────┘
                                   │
                        ┌──────────┴──────────┐
                        │                     │
                    Success               Failure
                        │                     │
                        ▼                     ▼
                 ┌────────────┐       ┌──────────────┐
                 │ status=    │       │ retries left?│
                 │ COMPLETED  │       └──────┬───────┘
                 │ Write exec │         Yes / No
                 │ record     │          │      │
                 └────────────┘          ▼      ▼
                                   PENDING  DEAD_LETTER
                                (backoff)   (terminal)
```

### 6.4 Worker Coordination Strategy (MVP)

For MVP, coordination is **intra-JVM only** using Java concurrency primitives:

- **Job claiming**: Atomic `UPDATE ... SET status='DISPATCHED', worker_id=? WHERE status='PENDING' AND scheduled_at <= NOW() LIMIT ?` — the database itself is the coordination layer
- **Thread pool**: `Executors.newFixedThreadPool(N)` with named threads
- **Shutdown**: `ExecutorService.shutdown()` + `awaitTermination()` on SIGTERM

> **Tradeoff**: This means only one JVM instance can run at a time. For MVP, this is acceptable. Multi-node coordination (via Redis `SETNX` or PostgreSQL advisory locks) is a post-MVP enhancement.

### 6.5 Scheduling Strategy

**Polling-based** — the dispatcher runs on a `@Scheduled(fixedDelay = 5000)` loop:

1. Query: `SELECT * FROM jobs WHERE status = 'PENDING' AND scheduled_at <= NOW() ORDER BY scheduled_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED`
2. For each job: update status to `DISPATCHED`, set `worker_id`, submit to pool
3. Sleep until next poll

**Why polling over event-driven?**
- Simpler to implement and reason about
- PostgreSQL doesn't natively support pub/sub for row changes (LISTEN/NOTIFY is limited)
- Polling with `SKIP LOCKED` is the industry-standard approach for job queues in PostgreSQL
- Latency of ≤5s is acceptable for MVP

### 6.6 Failure Handling Summary

| Failure Mode | Detection | Recovery |
|---|---|---|
| Job throws exception | `try/catch` in executor | Increment retry count, reschedule with backoff |
| Job exceeds timeout | `Future.get(timeout)` | Cancel thread, mark `FAILED`, trigger retry |
| JVM crashes mid-execution | Jobs stuck in `DISPATCHED`/`RUNNING` | Stale job reaper: periodic query for jobs in `RUNNING` for >2× timeout, reset to `PENDING` |
| Database unavailable | Connection pool exception | Dispatcher skips cycle, logs error, retries on next poll |
| Rate limiter exhausted | `tryConsume()` returns false | Job re-queued as `PENDING` with short delay |

---

## 7. Distributed Systems Considerations

### 7.1 Concurrency

**Problem**: Multiple dispatcher threads (or future nodes) could pick up the same job simultaneously.

**MVP Solution**: PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` provides row-level locking. Only one transaction can claim a given row. Other transactions skip it and move on. This is the simplest correct solution.

```sql
SELECT * FROM jobs
WHERE status = 'PENDING' AND scheduled_at <= NOW()
ORDER BY scheduled_at ASC
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

### 7.2 Race Conditions

| Race Condition | Scenario | Mitigation |
|---|---|---|
| Double pickup | Two pollers claim same job | `FOR UPDATE SKIP LOCKED` |
| Cancel vs Execute | User cancels while job is dispatched | Check status before execution; use optimistic locking (version column) |
| Retry vs Manual Retry | Auto-retry and manual retry fire simultaneously | Atomic status transition: only one `UPDATE` succeeds |

### 7.3 Clock Drift

**MVP simplification**: Single JVM, single clock. Clock drift is not a concern.

**Post-MVP note**: For multi-node, all workers should use the **database server's clock** (`NOW()` in SQL) rather than local `System.currentTimeMillis()`. This centralizes the time source.

### 7.4 Duplicate Execution Prevention

- Atomic claiming via `FOR UPDATE SKIP LOCKED` prevents duplicate pickup
- Status state machine enforces valid transitions (see §9)
- Each execution attempt gets a unique `execution_id` — even retries are distinct records

### 7.5 Worker Crashes

**Detection**: A stale job reaper runs every 60 seconds:
```sql
UPDATE jobs SET status = 'PENDING', worker_id = NULL
WHERE status = 'RUNNING'
AND updated_at < NOW() - INTERVAL '2 minutes';
```

**Tradeoff**: This implements **at-least-once** execution. A job that actually completed but whose status update failed will be re-executed. True exactly-once requires idempotent job design (documented as a consumer responsibility).

### 7.6 Retries

- Retry count is stored on the job record and incremented on each failure
- Backoff delay: `1s × 2^attempt` (1s, 2s, 4s, 8s...)
- Max retries configurable per job (default: 3)
- After max retries → `DEAD_LETTER` (terminal state, requires manual intervention)

### 7.7 Idempotency

**Distro Tasker guarantees at-least-once execution, not exactly-once.** This is a deliberate design choice:

- Exactly-once is impossible in a distributed system without two-phase commit
- At-least-once with idempotent jobs is the industry standard (Kafka, SQS, Celery)
- **Job submitters are responsible for making their commands idempotent** (documented in API docs)

### 7.8 Scalability Tradeoffs

| Approach | Throughput | Complexity | MVP? |
|---|---|---|---|
| Single-threaded | ~1 job/s | Trivial | No — too simple |
| **Thread pool (4–8 threads)** | **~50–100 jobs/s** | **Medium** | **Yes ✓** |
| Multi-node + Redis locks | ~500+ jobs/s | High | No — post-MVP |
| Message queue (RabbitMQ/Kafka) | ~10,000+ jobs/s | Very High | No — over-engineered |

---

## 8. Token Bucket Design

### 8.1 How the Algorithm Works

The Token Bucket algorithm controls throughput by maintaining a virtual "bucket" of tokens:

```
┌─────────────────────────────────────────────┐
│              TOKEN BUCKET                    │
│                                              │
│   Capacity: 10 tokens (max burst)           │
│   Refill Rate: 5 tokens/second              │
│                                              │
│   ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐            │
│   │●│ │●│ │●│ │●│ │●│ │●│ │●│  7/10 tokens │
│   └─┘ └─┘ └─┘ └─┘ └─┘ └─┘ └─┘            │
│                                              │
│   ▲ Refill: +5 tokens/sec (up to capacity) │
│   ▼ Consume: -1 token per job execution     │
│                                              │
│   If tokens == 0 → job is RE-QUEUED         │
└─────────────────────────────────────────────┘
```

**Rules:**
1. Bucket starts full (capacity = max tokens)
2. Every `1/refillRate` seconds, one token is added (up to capacity)
3. Before executing a job, call `tryConsume(1)`:
   - If tokens ≥ 1 → decrement, return `true`, job executes
   - If tokens == 0 → return `false`, job is re-queued
4. Burst: if bucket is full and 10 jobs arrive, all 10 execute immediately (draining the bucket)

### 8.2 Implementation

```java
public class TokenBucketRateLimiter {
    private final long capacity;
    private final double refillRate; // tokens per second
    private final AtomicLong availableTokens;
    private volatile long lastRefillTimestamp;
    private final Object lock = new Object();

    public boolean tryConsume(int tokens) {
        synchronized (lock) {
            refill();
            if (availableTokens.get() >= tokens) {
                availableTokens.addAndGet(-tokens);
                return true;
            }
            return false;
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTimestamp;
        long tokensToAdd = (long)(elapsed * refillRate / 1_000_000_000);
        if (tokensToAdd > 0) {
            availableTokens.set(
                Math.min(capacity, availableTokens.get() + tokensToAdd)
            );
            lastRefillTimestamp = now;
        }
    }
}
```

### 8.3 Refill Logic

**Lazy refill** — tokens are not added on a separate timer thread. Instead, `refill()` is called inside `tryConsume()` and calculates how many tokens *should have been* added since the last call.

**Why lazy?**
- No extra thread needed
- No timer drift issues
- Mathematically exact (based on elapsed nanoseconds)
- Simpler to test

### 8.4 Edge Cases

| Edge Case | Behavior |
|---|---|
| Bucket is empty, 100 jobs waiting | All return `false`, all re-queued. Bucket refills naturally. No starvation — FIFO re-queue order. |
| Burst after idle period | Bucket refills to capacity during idle. Burst is absorbed up to capacity. |
| Refill rate > dispatch rate | Bucket stays full. Rate limiter is effectively a no-op. |
| Capacity = 1, rate = 1/s | Strictly one job per second, zero burst. Useful for testing. |
| Configuration change at runtime | Expose via `/api/v1/config/rate-limiter` PUT endpoint. New values take effect on next `tryConsume()`. |

### 8.5 Where It Lives in the Architecture

The rate limiter sits **between the dispatcher and the executor**:

```
Dispatcher → claims job → submits to pool → worker thread starts →
  → tryConsume() → YES → execute job
                 → NO  → re-queue job as PENDING (with 1s delay)
```

It does NOT live at the API layer (that would throttle job *submission*, not *execution*).

### 8.6 Why Token Bucket Over Alternatives

| Algorithm | Pros | Cons | Verdict |
|---|---|---|---|
| **Token Bucket** | Allows bursts, smooth refill, simple to implement | Slightly complex state management | **✓ Selected** |
| Fixed Window | Very simple | Boundary spike problem (2× throughput at window edge) | ✗ |
| Sliding Window Log | Precise | Memory-intensive (stores all timestamps) | ✗ |
| Leaky Bucket | Smooth output | No burst allowance, harder to tune | ✗ |

Token Bucket is the industry standard for good reason: it balances burst tolerance with smooth throughput, and it's straightforward to implement correctly.

---

## 9. Database Design

### 9.1 Technology Choice

**PostgreSQL 15+** — chosen for:
- `FOR UPDATE SKIP LOCKED` support (critical for atomic job claiming)
- JSONB columns for flexible job metadata
- Excellent indexing and query planning
- Battle-tested in production job queue implementations

### 9.2 Schema

#### `jobs` Table

```sql
CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command         TEXT NOT NULL,
    command_type    VARCHAR(20) NOT NULL DEFAULT 'SHELL',  -- SHELL, HTTP
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority        INTEGER NOT NULL DEFAULT 5,            -- 1 (highest) to 10 (lowest)

    -- Scheduling
    scheduled_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,

    -- Retry configuration
    max_retries     INTEGER NOT NULL DEFAULT 3,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,

    -- Worker assignment
    worker_id       VARCHAR(50),

    -- Metadata
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Optimistic locking
    version         INTEGER NOT NULL DEFAULT 0
);
```

#### `job_executions` Table

```sql
CREATE TABLE job_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    attempt_number  INTEGER NOT NULL,
    worker_id       VARCHAR(50) NOT NULL,

    status          VARCHAR(20) NOT NULL,  -- RUNNING, COMPLETED, FAILED, TIMEOUT
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    duration_ms     BIGINT,

    exit_code       INTEGER,
    stdout          TEXT,  -- truncated to 10KB
    stderr          TEXT,  -- truncated to 10KB
    error_message   TEXT,

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

### 9.3 Indexes

```sql
-- Critical: dispatcher polling query
CREATE INDEX idx_jobs_pending_scheduled
    ON jobs (scheduled_at ASC)
    WHERE status = 'PENDING';

-- Job listing by status
CREATE INDEX idx_jobs_status ON jobs (status);

-- Execution history lookup
CREATE INDEX idx_executions_job_id ON job_executions (job_id);

-- Stale job reaper
CREATE INDEX idx_jobs_running_updated
    ON jobs (updated_at)
    WHERE status = 'RUNNING';
```

### 9.4 Job Status Lifecycle

```
                    ┌──────────────────────────────────────────────┐
                    │                                              │
                    ▼                                              │
  ┌─────────┐  ┌──────────┐  ┌─────────┐  ┌───────────┐         │
  │ PENDING │─▶│DISPATCHED│─▶│ RUNNING │─▶│ COMPLETED │         │
  └─────────┘  └──────────┘  └─────────┘  └───────────┘         │
       ▲                          │                               │
       │                          │ (failure)                     │
       │                          ▼                               │
       │                    ┌──────────┐                          │
       │◀───────────────────│  FAILED  │   (if retries remain)   │
       │    (reschedule     └──────────┘                          │
       │     with backoff)        │                               │
       │                          │ (max retries exceeded)        │
       │                          ▼                               │
       │                   ┌─────────────┐                        │
       │                   │ DEAD_LETTER │                        │
       │                   └─────────────┘                        │
       │                          │                               │
       │                          │ (manual retry)                │
       │◀─────────────────────────┘                               │
       │                                                          │
  ┌────┴─────┐                                                    │
  │CANCELLED │  (user cancels PENDING job)                        │
  └──────────┘                                                    │
```

**Valid Transitions:**

| From | To | Trigger |
|---|---|---|
| `PENDING` | `DISPATCHED` | Dispatcher claims job |
| `PENDING` | `CANCELLED` | User cancels via API |
| `DISPATCHED` | `RUNNING` | Worker starts execution |
| `DISPATCHED` | `PENDING` | Rate limiter rejects (re-queue) |
| `RUNNING` | `COMPLETED` | Job finishes successfully |
| `RUNNING` | `FAILED` | Job throws exception or times out |
| `RUNNING` | `PENDING` | Stale job reaper detects crash |
| `FAILED` | `PENDING` | Auto-retry with backoff |
| `FAILED` | `DEAD_LETTER` | Max retries exceeded |
| `DEAD_LETTER` | `PENDING` | Manual retry via API |

---

## 10. API Design

### Base URL: `/api/v1`

### 10.1 Create Job

```
POST /api/v1/jobs
```

**Request:**
```json
{
    "command": "echo 'Hello from Distro Tasker'",
    "commandType": "SHELL",
    "scheduledAt": "2026-05-12T10:30:00Z",
    "maxRetries": 3,
    "timeoutSeconds": 30,
    "priority": 5,
    "metadata": {
        "tag": "demo",
        "owner": "team-backend"
    }
}
```

**Response (201 Created):**
```json
{
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "PENDING",
    "command": "echo 'Hello from Distro Tasker'",
    "scheduledAt": "2026-05-12T10:30:00Z",
    "createdAt": "2026-05-11T14:55:00Z",
    "maxRetries": 3,
    "retryCount": 0,
    "timeoutSeconds": 30
}
```

### 10.2 Get Job

```
GET /api/v1/jobs/{id}
```

**Response (200 OK):**
```json
{
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "COMPLETED",
    "command": "echo 'Hello from Distro Tasker'",
    "scheduledAt": "2026-05-12T10:30:00Z",
    "startedAt": "2026-05-12T10:30:05Z",
    "completedAt": "2026-05-12T10:30:06Z",
    "workerId": "worker-3",
    "retryCount": 0,
    "maxRetries": 3,
    "executionCount": 1
}
```

### 10.3 List Jobs

```
GET /api/v1/jobs?status=PENDING&page=0&size=20&sort=scheduledAt,asc
```

**Response (200 OK):**
```json
{
    "content": [
        {
            "id": "...",
            "status": "PENDING",
            "command": "...",
            "scheduledAt": "..."
        }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
}
```

### 10.4 Cancel Job

```
DELETE /api/v1/jobs/{id}
```

**Response (200 OK):**
```json
{
    "id": "a1b2c3d4-...",
    "status": "CANCELLED",
    "cancelledAt": "2026-05-11T15:00:00Z"
}
```

**Error (409 Conflict):** if job is not in `PENDING` state:
```json
{
    "error": "INVALID_STATE_TRANSITION",
    "message": "Cannot cancel job in RUNNING state",
    "currentStatus": "RUNNING"
}
```

### 10.5 Retry Failed Job

```
POST /api/v1/jobs/{id}/retry
```

**Response (200 OK):**
```json
{
    "id": "a1b2c3d4-...",
    "status": "PENDING",
    "retryCount": 0,
    "scheduledAt": "2026-05-11T15:05:00Z",
    "message": "Job re-queued for immediate execution"
}
```

### 10.6 Get Execution History

```
GET /api/v1/jobs/{id}/executions
```

**Response (200 OK):**
```json
{
    "jobId": "a1b2c3d4-...",
    "executions": [
        {
            "id": "exec-001",
            "attemptNumber": 1,
            "workerId": "worker-2",
            "status": "FAILED",
            "startedAt": "2026-05-12T10:30:05Z",
            "completedAt": "2026-05-12T10:30:35Z",
            "durationMs": 30000,
            "errorMessage": "TIMEOUT: exceeded 30s limit"
        },
        {
            "id": "exec-002",
            "attemptNumber": 2,
            "workerId": "worker-1",
            "status": "COMPLETED",
            "startedAt": "2026-05-12T10:30:37Z",
            "completedAt": "2026-05-12T10:30:38Z",
            "durationMs": 1200,
            "exitCode": 0,
            "stdout": "Hello from Distro Tasker"
        }
    ]
}
```

### 10.7 Health Check

```
GET /api/v1/health
```

**Response (200 OK):**
```json
{
    "status": "UP",
    "components": {
        "database": "UP",
        "workerPool": {
            "status": "UP",
            "activeThreads": 2,
            "poolSize": 4,
            "queuedTasks": 0
        },
        "rateLimiter": {
            "status": "UP",
            "availableTokens": 7,
            "capacity": 10,
            "refillRate": 5.0
        }
    },
    "uptime": "2h 15m 30s"
}
```

### 10.8 Metrics

```
GET /api/v1/metrics
```

**Response (200 OK):**
```json
{
    "jobs": {
        "total": 150,
        "pending": 12,
        "running": 3,
        "completed": 120,
        "failed": 10,
        "deadLetter": 5
    },
    "execution": {
        "avgDurationMs": 2340,
        "p99DurationMs": 15000,
        "totalExecutions": 175,
        "successRate": 0.92
    },
    "rateLimiter": {
        "tokensConsumed": 145,
        "jobsThrottled": 23,
        "currentTokens": 7
    },
    "workers": {
        "activeCount": 2,
        "completedTaskCount": 148,
        "poolSize": 4
    }
}
```

### 10.9 Update Rate Limiter Configuration

```
PUT /api/v1/config/rate-limiter
```

**Request:**
```json
{
    "capacity": 20,
    "refillRate": 10.0
}
```

**Response (200 OK):**
```json
{
    "capacity": 20,
    "refillRate": 10.0,
    "currentTokens": 20,
    "message": "Rate limiter configuration updated"
}
```

---

## 11. Execution Flow

### Step-by-Step Job Lifecycle

```
 Step 1          Step 2          Step 3           Step 4
 ──────          ──────          ──────           ──────
 Client          API Server      PostgreSQL       Background
 submits job     validates &     persists job     dispatcher
 via POST        enriches        status=PENDING   polls every 5s
     │               │               │                │
     ▼               ▼               ▼                ▼
 ┌───────┐     ┌───────────┐   ┌──────────┐    ┌──────────┐
 │ POST  │────▶│ Validate  │──▶│  INSERT  │    │  SELECT  │
 │/jobs  │     │ + enrich  │   │  INTO    │    │  due     │
 └───────┘     │ defaults  │   │  jobs    │    │  jobs    │
               └───────────┘   └──────────┘    └────┬─────┘
                                                     │
 Step 5          Step 6          Step 7           Step 8
 ──────          ──────          ──────           ──────
 Claim job       Submit to       Token Bucket     Execute
 atomically      worker pool     gate check       command
     │               │               │                │
     ▼               ▼               ▼                ▼
 ┌──────────┐  ┌───────────┐   ┌──────────┐    ┌──────────┐
 │ UPDATE   │  │ executor  │   │tryConsume │    │ProcessB. │
 │ status=  │──▶│ .submit() │──▶│ (1)      │──▶│ .start() │
 │DISPATCHED│  └───────────┘   └──────────┘    │ capture  │
 └──────────┘                   │    │          │ stdout   │
                            yes ▼    ▼ no       └────┬─────┘
                          execute  re-queue           │
                                  as PENDING     Step 9
                                                 ──────
                                                 Record
                                                 result
                                                     │
                                                     ▼
                                               ┌──────────┐
                                               │  INSERT   │
                                               │  exec     │
                                               │  record   │
                                               └────┬─────┘
                                                     │
                                              ┌──────┴──────┐
                                              │             │
                                          success       failure
                                              │             │
                                              ▼             ▼
                                        Step 10       Step 11
                                        ──────        ──────
                                        Mark          Retry?
                                        COMPLETED         │
                                                    ┌─────┴─────┐
                                                    │           │
                                                retries     max retries
                                                remain      exceeded
                                                    │           │
                                                    ▼           ▼
                                              reschedule   DEAD_LETTER
                                              w/ backoff   (terminal)
```

### Detailed Steps

| Step | Action | Component | DB Change | Error Handling |
|---|---|---|---|---|
| 1 | Client sends `POST /api/v1/jobs` | REST Controller | — | Return 400 for invalid input |
| 2 | Validate input, set defaults | Job Service | — | Reject past `scheduledAt` |
| 3 | Persist to database | Job Repository | `INSERT INTO jobs` status=`PENDING` | Return 500 if DB unavailable |
| 4 | Dispatcher polls for due jobs | Job Dispatcher | `SELECT ... FOR UPDATE SKIP LOCKED` | Skip cycle on DB error |
| 5 | Claim job atomically | Job Dispatcher | `UPDATE status='DISPATCHED'` | Row already claimed → skip |
| 6 | Submit to worker thread pool | Worker Pool | — | Queue full → log warning, job stays `DISPATCHED` |
| 7 | Check rate limiter | Token Bucket | — | No token → re-queue as `PENDING` w/ 1s delay |
| 8 | Execute shell command | Job Executor | `UPDATE status='RUNNING'` | Process timeout → cancel + mark `FAILED` |
| 9 | Capture stdout/stderr, exit code | Job Executor | `INSERT INTO job_executions` | Truncate output to 10KB |
| 10 | Mark job completed | Job Service | `UPDATE status='COMPLETED'` | — |
| 11 | Handle failure / retry | Job Service | `UPDATE status='FAILED'` or `'DEAD_LETTER'` | Backoff: `1s × 2^attempt` |

---

## 12. Reliability + Fault Tolerance

### 12.1 Retry Strategy

```
Attempt 1 → fail → wait 1s → Attempt 2 → fail → wait 2s → Attempt 3 → fail → wait 4s → DEAD_LETTER
```

| Config | Default | Range | Notes |
|---|---|---|---|
| `maxRetries` | 3 | 0–10 | Per-job, set at creation |
| `baseDelay` | 1 second | — | System-wide |
| `backoffMultiplier` | 2 | — | Exponential: `baseDelay × 2^attempt` |
| `maxDelay` | 60 seconds | — | Cap to prevent excessive waits |

**Implementation**: On failure, the job's `scheduledAt` is updated to `NOW() + backoffDelay`, status is reset to `PENDING`, and `retryCount` is incremented. The dispatcher will naturally pick it up after the delay.

### 12.2 Dead-Letter Handling

Jobs that exceed `maxRetries` are moved to `DEAD_LETTER` status:

- They are **not deleted** — preserved for debugging
- Queryable via `GET /api/v1/jobs?status=DEAD_LETTER`
- Can be manually retried via `POST /api/v1/jobs/{id}/retry` (resets retry count)
- Dead-letter count is tracked in metrics

### 12.3 Crash Recovery

| Scenario | Detection | Recovery |
|---|---|---|
| JVM crashes during execution | Jobs stuck in `RUNNING` beyond `2 × timeoutSeconds` | Stale job reaper resets to `PENDING` |
| JVM crashes after dispatch | Jobs stuck in `DISPATCHED` for >60s | Same reaper handles `DISPATCHED` staleness |
| DB connection lost | `SQLException` in dispatcher | Dispatcher logs error, retries on next poll cycle |
| Thread pool exhausted | `RejectedExecutionException` | Job stays in `DISPATCHED`; picked up on next cycle after threads free |

**Stale Job Reaper** — runs every 60 seconds:
```sql
-- Reset running jobs that appear crashed
UPDATE jobs
SET status = 'PENDING', worker_id = NULL, version = version + 1
WHERE status IN ('RUNNING', 'DISPATCHED')
  AND updated_at < NOW() - INTERVAL '2 minutes';
```

### 12.4 Exactly-Once vs At-Least-Once

| Guarantee | How | Tradeoff |
|---|---|---|
| **At-least-once** ✓ (MVP) | Retry on failure, reaper recovers stuck jobs | Jobs may execute >1 time after crashes |
| Exactly-once ✗ | Would require distributed transactions or idempotency keys | Too complex for MVP |

**Mitigation**: Document that job commands should be idempotent. Provide an `idempotencyKey` field in metadata for consumers who need deduplication.

### 12.5 Distributed Locking (MVP Scope)

For MVP (single JVM), PostgreSQL `FOR UPDATE SKIP LOCKED` is the only lock needed. No Redis, no ZooKeeper.

**Post-MVP**: For multi-node, introduce Redis-based distributed locks:
```java
// Post-MVP: Redis SETNX for cross-node coordination
boolean locked = redis.setIfAbsent("lock:job:" + jobId, workerId, 60, SECONDS);
```

### 12.6 Graceful Degradation

| Degraded State | Behavior |
|---|---|
| Rate limiter fully drained | Jobs queue up; execution resumes as tokens refill. No jobs are lost. |
| All workers busy | Dispatcher stops claiming new jobs (backpressure). Existing jobs complete normally. |
| Database slow | Poll interval naturally adapts (fixed delay, not fixed rate). Throughput decreases but system remains stable. |

---

## 13. Observability

### 13.1 Structured Logging

All log entries use structured JSON format via Logback:

```json
{
    "timestamp": "2026-05-12T10:30:05.123Z",
    "level": "INFO",
    "logger": "JobExecutor",
    "thread": "worker-3",
    "jobId": "a1b2c3d4-...",
    "event": "JOB_STARTED",
    "message": "Starting execution of job a1b2c3d4",
    "metadata": {
        "command": "echo hello",
        "attemptNumber": 1,
        "scheduledAt": "2026-05-12T10:30:00Z",
        "actualStartDelay": "5123ms"
    }
}
```

**Key log events:**

| Event | Level | When |
|---|---|---|
| `JOB_CREATED` | INFO | Job submitted via API |
| `JOB_DISPATCHED` | INFO | Dispatcher claims job |
| `JOB_STARTED` | INFO | Worker begins execution |
| `JOB_COMPLETED` | INFO | Job finishes successfully |
| `JOB_FAILED` | WARN | Job execution fails |
| `JOB_RETRY` | WARN | Job re-queued for retry |
| `JOB_DEAD_LETTER` | ERROR | Job exceeded max retries |
| `JOB_TIMEOUT` | WARN | Job exceeded timeout |
| `RATE_LIMITED` | INFO | Token bucket rejected job |
| `STALE_JOB_RECOVERED` | WARN | Reaper recovered stuck job |

### 13.2 In-Memory Metrics

Implemented using `AtomicLong` counters — no external metrics stack needed:

```java
@Component
public class MetricsCollector {
    private final AtomicLong jobsCreated = new AtomicLong();
    private final AtomicLong jobsCompleted = new AtomicLong();
    private final AtomicLong jobsFailed = new AtomicLong();
    private final AtomicLong jobsThrottled = new AtomicLong();
    private final AtomicLong tokensConsumed = new AtomicLong();
    private final AtomicLong totalExecutionTimeMs = new AtomicLong();
    // ... getters, increment methods, snapshot method
}
```

Exposed via `GET /api/v1/metrics` (see §10.8).

### 13.3 Tracing Ideas (Post-MVP)

- Assign a `traceId` to each job at creation, propagate through all execution records
- For MVP, the `jobId` serves as a simple trace correlation ID
- Post-MVP: integrate with OpenTelemetry for distributed tracing

### 13.4 Execution Audit Trail

The `job_executions` table IS the audit trail:

- Every attempt is a separate, immutable record
- Includes stdout/stderr capture
- Queryable via API and direct SQL
- Can be exported for post-mortem analysis

### 13.5 Dashboard Concept (Post-MVP)

For MVP, metrics are API-only. Post-MVP dashboard would show:
- Real-time job throughput graph
- Worker pool utilization
- Rate limiter token level
- Failed job queue depth
- Execution latency histogram

---

## 14. Security Considerations

### 14.1 Script Execution Risks

> [!CAUTION]
> **Executing arbitrary shell commands is inherently dangerous.** Distro Tasker MVP assumes a trusted, single-tenant environment. Do NOT expose the API to untrusted users without additional safeguards.

### 14.2 MVP Security Measures

| Risk | Mitigation |
|---|---|
| Arbitrary command injection | MVP: trusted environment only. Post-MVP: command allowlist |
| Resource exhaustion (fork bomb) | Per-job timeout enforced via `Future.get(timeout)` + `Process.destroyForcibly()` |
| Large output consumption | stdout/stderr truncated to 10KB |
| API abuse (job spam) | Rate limit on API endpoints via Spring filter (separate from execution rate limiter) |
| Sensitive data in commands | Log commands at INFO level but provide option to redact via metadata flag |

### 14.3 Sandboxing Ideas (Post-MVP)

- Execute commands in Docker containers with resource limits (`--memory`, `--cpus`)
- Use `ProcessBuilder` with restricted environment variables
- Run worker processes under a low-privilege OS user
- Implement command allowlists/blocklists

### 14.4 Authentication (Post-MVP)

MVP has no authentication. Post-MVP:
- API key authentication via header (`X-API-Key`)
- Optional JWT for multi-tenant support
- Role-based access: `admin` (full access) vs `user` (own jobs only)

### 14.5 Rate Limiting Abuse Cases

| Abuse Scenario | Mitigation |
|---|---|
| Reconfiguring rate limiter to `capacity=999999` | Validate max capacity in config endpoint (e.g., max 100) |
| Flooding API with job creation requests | Separate API rate limiter (e.g., 100 requests/minute per IP) |
| Creating jobs with very long timeouts | Validate `timeoutSeconds` max (e.g., 300s) |
| Creating thousands of pending jobs | Set per-user job limit (post-MVP) |

---

## 15. MVP Scope vs Future Scope

| Feature | MVP (Weeks 1–6) | Post-MVP | Explicitly Deferred |
|---|---|---|---|
| Job submission API | ✅ | — | — |
| Shell command execution | ✅ | — | — |
| Scheduled dispatch (polling) | ✅ | — | — |
| Concurrent worker pool | ✅ (threads) | Multi-node workers | — |
| Token Bucket rate limiter | ✅ | — | — |
| Retry with exponential backoff | ✅ | — | — |
| Dead-letter queue | ✅ | — | — |
| Execution history / audit trail | ✅ | — | — |
| Job cancellation | ✅ | — | — |
| Manual retry | ✅ | — | — |
| Health endpoint | ✅ | — | — |
| In-memory metrics | ✅ | Prometheus export | — |
| Structured logging | ✅ | ELK integration | — |
| Stale job reaper | ✅ | — | — |
| Rate limiter config endpoint | ✅ | — | — |
| Docker Compose setup | ✅ | — | — |
| HTTP command type | — | ✅ | — |
| Recurring/cron jobs | — | — | ✅ (too complex) |
| Web dashboard | — | — | ✅ (unrelated scope) |
| Multi-node clustering | — | ✅ | — |
| Redis distributed locks | — | ✅ | — |
| User authentication | — | ✅ | — |
| Docker sandboxing | — | — | ✅ (ops complexity) |
| Priority queues | — | ✅ | — |
| Webhook notifications | — | ✅ | — |
| OpenTelemetry tracing | — | — | ✅ (infra overhead) |
| gRPC API | — | — | ✅ (unnecessary) |
| Kubernetes deployment | — | — | ✅ (over-engineered) |

---

## 16. Development Roadmap

> [!IMPORTANT]
> This roadmap assumes ~20–25 hours/week of focused development time. Each week ends with a demoable checkpoint.

### Week 1: Foundation & Core Persistence

**Goal**: Project scaffolding, database setup, and job CRUD API.

| Day | Deliverable |
|---|---|
| 1–2 | Spring Boot project init, PostgreSQL via Docker Compose, Flyway migration setup |
| 3–4 | `jobs` table schema, `Job` entity, `JobRepository` with Spring Data JPA |
| 5–6 | `POST /api/v1/jobs` — create job with validation |
| 7 | `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`, `DELETE /api/v1/jobs/{id}` (cancel) |

**Demo Checkpoint**: Submit a job via curl, see it persisted in the database, query and cancel it via API.

**Risks**: Docker/PostgreSQL environment issues on dev machine. **Mitigation**: Provide an H2 fallback for local dev.

**Tests**: Unit tests for validation logic, integration tests for repository layer.

---

### Week 2: Dispatcher & Worker Pool

**Goal**: Jobs execute automatically at their scheduled time.

| Day | Deliverable |
|---|---|
| 1–2 | `JobDispatcher` — `@Scheduled` poller with `FOR UPDATE SKIP LOCKED` query |
| 3–4 | `WorkerPool` — `ExecutorService` with configurable thread count and named threads |
| 5–6 | `JobExecutor` — execute shell commands via `ProcessBuilder`, capture stdout/stderr |
| 7 | `job_executions` table, `ExecutionRepository`, `GET /api/v1/jobs/{id}/executions` |

**Demo Checkpoint**: Submit a job scheduled 10 seconds in the future → watch it auto-execute → query execution history showing output.

**Risks**: `ProcessBuilder` platform differences (Windows vs Linux). **Mitigation**: Test on both; Docker standardizes to Linux.

**Tests**: Unit tests for dispatcher claiming logic, integration test for end-to-end job execution.

---

### Week 3: Rate Limiter & Retry Logic

**Goal**: Token Bucket controls execution throughput; failures retry automatically.

| Day | Deliverable |
|---|---|
| 1–2 | `TokenBucketRateLimiter` — thread-safe implementation with lazy refill |
| 3 | Integration: rate limiter gate between dispatch and execution, re-queue on rejection |
| 4–5 | Retry logic — exponential backoff, `retryCount` tracking, `DEAD_LETTER` transition |
| 6 | Per-job timeout via `Future.get(timeout)` + `Process.destroyForcibly()` |
| 7 | `PUT /api/v1/config/rate-limiter` endpoint for runtime config |

**Demo Checkpoint**: Submit 20 jobs with capacity=5 → watch them execute in controlled batches. Submit a job that always fails → watch it retry 3 times with increasing delays → end up in `DEAD_LETTER`.

**Risks**: Thread-safety bugs in rate limiter. **Mitigation**: Write concurrent unit tests with `CountDownLatch`.

**Tests**: Unit tests for Token Bucket (capacity, refill, edge cases), concurrent access tests, retry backoff calculation tests.

---

### Week 4: Observability & Fault Tolerance

**Goal**: System is observable and recovers from failures.

| Day | Deliverable |
|---|---|
| 1–2 | `MetricsCollector` with `AtomicLong` counters, `GET /api/v1/metrics` |
| 3 | `GET /api/v1/health` with DB, worker pool, and rate limiter status |
| 4 | Structured JSON logging via Logback config — all key events logged with jobId context |
| 5–6 | Stale job reaper — detect and recover stuck `RUNNING`/`DISPATCHED` jobs |
| 7 | `POST /api/v1/jobs/{id}/retry` — manual retry for `FAILED`/`DEAD_LETTER` jobs |

**Demo Checkpoint**: Show health endpoint, metrics dashboard data, trigger a simulated crash recovery via the stale job reaper.

**Risks**: Reaper accidentally resetting jobs that are legitimately slow. **Mitigation**: Use `2 × timeoutSeconds` as staleness threshold.

**Tests**: Integration tests for crash recovery, metrics accuracy tests.

---

### Week 5: Integration, Hardening & Docker

**Goal**: Production-ready packaging and end-to-end testing.

| Day | Deliverable |
|---|---|
| 1–2 | Full `docker-compose.yml` — app + PostgreSQL, environment config, health checks |
| 3 | `Dockerfile` — multi-stage build for Spring Boot |
| 4–5 | End-to-end integration tests — full lifecycle from creation to completion/dead-letter |
| 6 | Concurrency stress tests — 100+ jobs, verify no duplicates, correct rate limiting |
| 7 | Error handling sweep — edge cases, validation errors, graceful API error responses |

**Demo Checkpoint**: `docker-compose up` from clean state → run full demo script → all features working.

**Risks**: Docker networking issues, flaky tests under concurrent load. **Mitigation**: Pin Docker versions, add test retries for timing-sensitive tests.

**Tests**: Load tests, concurrency correctness tests, Docker smoke tests.

---

### Week 6: Polish, Documentation & Demo

**Goal**: Portfolio-ready presentation.

| Day | Deliverable |
|---|---|
| 1–2 | `README.md` — architecture overview, quickstart, API docs, design decisions |
| 3 | Demo script — automated curl commands showcasing all features |
| 4 | Code cleanup — consistent naming, Javadoc for public APIs, remove dead code |
| 5 | Record demo video / GIF — terminal session showing scheduling, rate limiting, retries |
| 6–7 | Final testing, bug fixes, tag `v1.0.0` release |

**Demo Checkpoint**: Complete demo walkthrough with narration covering all 6 core distributed systems concepts.

**Tests**: Final regression pass, README quickstart verification on clean machine.

---

### Roadmap Visual

```
Week 1        Week 2        Week 3        Week 4        Week 5        Week 6
──────        ──────        ──────        ──────        ──────        ──────
Foundation    Execution     Rate Limit    Observability Integration   Polish
& CRUD API    Engine        & Retry       & Recovery    & Docker      & Demo

[██████████] [██████████] [██████████] [██████████] [██████████] [██████████]
 DB Schema    Dispatcher    Token Bucket  Metrics       Docker        README
 Job API      Worker Pool   Retry Logic   Health        Compose       Demo Script
 Validation   Executor      Timeout       Logging       E2E Tests     Cleanup
 CRUD         Exec History  Config API    Reaper        Load Tests    v1.0.0
                                          Manual Retry  Hardening     Video

 ▲ Demo 1     ▲ Demo 2      ▲ Demo 3      ▲ Demo 4      ▲ Demo 5      ▲ Final
 CRUD via     Auto-exec     Rate limit    Crash         docker-       Full
 curl         + history     + retry       recovery      compose up    walkthrough
```

---

## 17. Technical Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| 1 | **Concurrency bugs** in dispatcher or rate limiter | High | High | Extensive concurrent unit tests; use `CountDownLatch` and `CyclicBarrier` for deterministic testing |
| 2 | **Thread starvation** if all workers are blocked on slow jobs | Medium | High | Enforce per-job timeouts; monitor active thread count; set pool size ≥ 4 |
| 3 | **Scheduling drift** — jobs execute significantly later than `scheduledAt` | Medium | Medium | Log `actualStartDelay` metric; keep poll interval ≤ 5s; monitor with metrics |
| 4 | **Rate limiter allows over-throughput** due to race conditions | Medium | Medium | `synchronized` block in `tryConsume()`; verify with concurrent stress tests |
| 5 | **Database contention** under high job volume | Low | Medium | `SKIP LOCKED` prevents row-level contention; partial indexes minimize scan scope |
| 6 | **ProcessBuilder deadlock** if stdout/stderr buffers fill | Medium | High | Read stdout/stderr on separate threads; truncate to 10KB; enforce timeout |
| 7 | **Stale job reaper false positives** | Low | Medium | Use generous staleness threshold (2 × timeout); log all reaper actions |
| 8 | **Docker environment differences** (dev vs CI vs demo) | Medium | Low | Pin all versions in Docker Compose; test on CI early |
| 9 | **Optimistic locking conflicts** during high-contention updates | Low | Low | Retry on `OptimisticLockException`; conflicts are rare in single-JVM setup |
| 10 | **Memory leak** from accumulated metrics or execution records | Low | Medium | Use `AtomicLong` (fixed memory); paginate execution history queries |

---

## 18. Testing Strategy

### 18.1 Test Pyramid

```
              ┌─────────────┐
              │   E2E / Load │  ← 5-10 tests
              │   Tests      │
              ├─────────────┤
              │ Integration  │  ← 15-20 tests
              │ Tests        │
              ├─────────────┤
              │  Unit Tests  │  ← 40-50 tests
              └─────────────┘
```

### 18.2 Unit Tests

| Component | What to Test | Count |
|---|---|---|
| `TokenBucketRateLimiter` | Capacity limits, refill correctness, concurrent access, edge cases | 8–10 |
| `JobService` | Status transitions, validation, retry logic, backoff calculation | 10–12 |
| `JobDispatcher` | Claiming logic, batch size, backpressure awareness | 5–7 |
| `JobExecutor` | Command execution, timeout handling, output capture | 5–7 |
| `MetricsCollector` | Counter accuracy, concurrent increments, snapshot consistency | 3–5 |
| Request validation | Invalid inputs, missing fields, future date enforcement | 5–8 |

### 18.3 Integration Tests

| Scenario | What It Verifies |
|---|---|
| Job creation → dispatch → execution → completion | Full happy path lifecycle |
| Job creation → dispatch → failure → retry → completion | Retry with backoff works correctly |
| Job creation → dispatch → 3 failures → dead letter | Max retries respected, dead letter transition |
| Job cancel while pending | Status updated, job not executed |
| Rate limiter rejects job → re-queue → eventual execution | Rate limiting + re-queue flow |
| Stale job reaper recovers stuck job | Crash recovery works |
| Concurrent dispatch of 50 jobs → no duplicates | `FOR UPDATE SKIP LOCKED` correctness |
| Execution history contains all attempts | Audit trail completeness |

### 18.4 Concurrency Tests

```java
@Test
void rateLimiterIsThreadSafe() {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 0); // 10 tokens, no refill
    AtomicInteger consumed = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

    // 20 threads try to consume 1 token each
    ExecutorService pool = Executors.newFixedThreadPool(20);
    for (int i = 0; i < 20; i++) {
        pool.submit(() -> {
            latch.await();
            if (limiter.tryConsume(1)) consumed.incrementAndGet();
        });
    }
    latch.countDown(); // release all threads simultaneously
    pool.shutdown();
    pool.awaitTermination(5, SECONDS);

    assertEquals(10, consumed.get()); // exactly 10 should succeed
}
```

### 18.5 Load Tests

| Test | Setup | Expected |
|---|---|---|
| Throughput | Submit 500 jobs scheduled for NOW | All complete within `500 / refillRate` seconds |
| Sustained load | Submit 10 jobs/second for 60 seconds | Rate limiter smooths to configured throughput |
| Recovery | Kill JVM mid-execution, restart | Stale reaper recovers all stuck jobs |

**Tool**: JUnit + `ExecutorService` for load generation. No external load testing framework needed for MVP.

### 18.6 Failure Injection Tests

| Test | Injection | Expected Behavior |
|---|---|---|
| DB connection drop | Stop PostgreSQL container during dispatch | Dispatcher logs error, retries on next cycle |
| Command always fails | `exit 1` command | 3 retries → dead letter |
| Command hangs forever | `sleep 9999` command | Timeout triggers, job marked FAILED |
| Stdout flood | `yes` command (infinite output) | Output truncated to 10KB, job eventually times out |

---

## 19. Demo Scenarios

### Demo 1: Basic Scheduling & Execution

**Purpose**: Show that jobs execute at the right time.

```bash
# Submit a job scheduled 15 seconds from now
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "command": "echo \"Hello from Distro Tasker! Time: $(date)\"",
    "scheduledAt": "'$(date -u -d '+15 seconds' +%Y-%m-%dT%H:%M:%SZ)'"
  }'

# Watch logs — job should execute ~15 seconds later
# Then check execution history
curl http://localhost:8080/api/v1/jobs/{id}/executions | jq
```

**What it demonstrates**: Scheduled persistence, polling dispatch, command execution, output capture.

---

### Demo 2: Rate Limiting Under Load

**Purpose**: Show Token Bucket controlling execution throughput.

```bash
# Set rate limiter: capacity=3, refill=1/second
curl -X PUT http://localhost:8080/api/v1/config/rate-limiter \
  -H "Content-Type: application/json" \
  -d '{"capacity": 3, "refillRate": 1.0}'

# Submit 20 jobs scheduled for NOW
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:8080/api/v1/jobs \
    -H "Content-Type: application/json" \
    -d "{\"command\": \"echo Job-$i completed\", \"scheduledAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"
done

# Watch metrics — jobs complete in controlled batches of ~3, then 1/second
watch -n 1 'curl -s http://localhost:8080/api/v1/metrics | jq .rateLimiter'
```

**What it demonstrates**: Token Bucket rate limiting, burst absorption, smooth throughput control, re-queuing.

---

### Demo 3: Retry & Dead Letter

**Purpose**: Show automatic retry with exponential backoff.

```bash
# Submit a job that will fail (exit code 1)
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "command": "exit 1",
    "scheduledAt": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "maxRetries": 3
  }'

# Watch execution history grow: attempt 1 (fail), attempt 2 (fail, +2s), attempt 3 (fail, +4s)
# Final status: DEAD_LETTER
watch -n 2 'curl -s http://localhost:8080/api/v1/jobs/{id} | jq "{status, retryCount}"'

# Manually retry the dead-lettered job
curl -X POST http://localhost:8080/api/v1/jobs/{id}/retry
```

**What it demonstrates**: Exponential backoff, retry tracking, dead-letter handling, manual recovery.

---

### Demo 4: Concurrent Execution

**Purpose**: Show multiple workers executing jobs in parallel.

```bash
# Submit 8 jobs that each take 3 seconds (worker pool = 4 threads)
for i in $(seq 1 8); do
  curl -s -X POST http://localhost:8080/api/v1/jobs \
    -H "Content-Type: application/json" \
    -d "{\"command\": \"sleep 3 && echo Worker-\$(hostname)-$i done\", \"scheduledAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"
done

# Watch health — 4 active threads, first 4 complete in ~3s, next 4 in ~6s
watch -n 1 'curl -s http://localhost:8080/api/v1/health | jq .components.workerPool'
```

**What it demonstrates**: Thread pool concurrency, parallel execution, worker utilization.

---

### Demo 5: Crash Recovery (Stale Job Reaper)

**Purpose**: Show system recovering from simulated worker failure.

```bash
# Submit a long-running job
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "command": "sleep 300",
    "scheduledAt": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "timeoutSeconds": 10
  }'

# Wait for it to start running, then check status
curl http://localhost:8080/api/v1/jobs/{id} | jq .status  # "RUNNING"

# The timeout will fire after 10s, marking it FAILED
# If we simulated a crash (kill -9), the stale reaper would recover it after 2 minutes

# Check metrics for recovered jobs
curl http://localhost:8080/api/v1/metrics | jq
```

**What it demonstrates**: Timeout enforcement, stale job detection, crash recovery.

---

### Demo 6: Full Observability

**Purpose**: Show monitoring and audit capabilities.

```bash
# Health check
curl http://localhost:8080/api/v1/health | jq

# System metrics
curl http://localhost:8080/api/v1/metrics | jq

# Execution audit trail for a specific job
curl http://localhost:8080/api/v1/jobs/{id}/executions | jq

# Structured logs (in Docker)
docker-compose logs -f app | jq .
```

**What it demonstrates**: Production-readiness, monitoring, debugging capabilities.

---

## 20. Resume / Portfolio Positioning

### 20.1 What This Project Demonstrates

| Concept | How It's Demonstrated | Interview Relevance |
|---|---|---|
| **Distributed Scheduling** | Polling dispatcher with atomic claiming, `FOR UPDATE SKIP LOCKED` | Core to any backend/infra role |
| **Concurrency** | Thread pool management, race condition prevention, `synchronized` blocks | Asked in ~80% of backend interviews |
| **Rate Limiting** | Token Bucket implementation with burst handling and lazy refill | Classic system design question |
| **Fault Tolerance** | Retries, exponential backoff, dead-letter queue, crash recovery | Demonstrates production thinking |
| **API Design** | RESTful lifecycle management, pagination, error handling, versioning | Every backend role |
| **Database Design** | Schema design, partial indexes, optimistic locking, status state machine | Core competency |
| **Observability** | Structured logging, in-memory metrics, health checks, audit trail | DevOps/SRE awareness |
| **Clean Architecture** | Layered monolith with clear component boundaries | Demonstrates software craft |

### 20.2 How to Present It

**Resume Bullet Points:**

> **Distro Tasker** — Distributed Task Scheduling Engine
> - Designed and built a multi-threaded task scheduler with Token Bucket rate limiting, supporting 50-100 concurrent jobs/sec with configurable throughput control
> - Implemented atomic job claiming via PostgreSQL `FOR UPDATE SKIP LOCKED`, preventing duplicate execution in concurrent dispatch scenarios
> - Built retry engine with exponential backoff and dead-letter queue handling, achieving at-least-once execution guarantees
> - Created comprehensive observability layer with structured logging, in-memory metrics, and execution audit trail

**LinkedIn/Portfolio Description:**

> Built a distributed task scheduling engine that demonstrates core backend infrastructure concepts: concurrent execution via managed thread pools, rate limiting via Token Bucket algorithm, fault tolerance through retry logic and crash recovery, and production observability. The system accepts jobs via REST API, schedules them for future execution, and dispatches them to a configurable worker pool — all while enforcing throughput limits and providing full execution audit trails.

### 20.3 What Interviewers Will Find Impressive

1. **You built a real scheduler, not a wrapper around `cron`** — Shows you understand the underlying problems
2. **Token Bucket is a classic interview question** — You have a working implementation you can whiteboard from memory
3. **`FOR UPDATE SKIP LOCKED`** — Shows deep PostgreSQL knowledge; most candidates don't know this exists
4. **State machine for job lifecycle** — Demonstrates structured thinking about status transitions and edge cases
5. **Rate limiter placement decision** — Between dispatcher and executor (not at API layer) shows architectural reasoning
6. **At-least-once vs exactly-once tradeoff discussion** — Shows maturity in distributed systems thinking
7. **Stale job reaper** — Production-grade failure handling that most student projects don't have
8. **The system runs via `docker-compose up`** — Easy for reviewers to try; removes friction

### 20.4 Interview Talking Points

Prepare answers for these questions (all answerable from this project):

| Question | Your Answer Source |
|---|---|
| "How would you design a distributed task scheduler?" | Your entire architecture |
| "Explain the Token Bucket algorithm" | §8 — implementation, tradeoffs, edge cases |
| "How do you handle concurrent access to shared resources?" | §7 — `FOR UPDATE SKIP LOCKED`, `synchronized` |
| "What's the difference between at-least-once and exactly-once?" | §7.7, §12.4 |
| "How do you handle failures in a distributed system?" | §12 — retries, dead letter, crash recovery |
| "How would you rate limit an API?" | §8 — explain Token Bucket AND why you put it at the execution layer |
| "How do you prevent duplicate execution?" | §7.4 — atomic claiming, status state machine |
| "How would you scale this system?" | §7.8 — thread pool → multi-node → message queue |

---

## Appendix: Quick Reference

### Technology Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 17+ |
| Framework | Spring Boot | 3.x |
| Database | PostgreSQL | 15+ |
| Migration | Flyway | Latest |
| Build | Maven or Gradle | Latest |
| Container | Docker + Docker Compose | Latest |
| Testing | JUnit 5 + Mockito + Testcontainers | Latest |
| Logging | SLF4J + Logback | (via Spring Boot) |

### Configuration Defaults

| Property | Default | Environment Variable |
|---|---|---|
| `dispatcher.pollInterval` | 5000ms | `DISPATCHER_POLL_INTERVAL` |
| `dispatcher.batchSize` | 10 | `DISPATCHER_BATCH_SIZE` |
| `worker.poolSize` | 4 | `WORKER_POOL_SIZE` |
| `rateLimiter.capacity` | 10 | `RATE_LIMITER_CAPACITY` |
| `rateLimiter.refillRate` | 5.0 | `RATE_LIMITER_REFILL_RATE` |
| `job.defaultTimeout` | 60s | `JOB_DEFAULT_TIMEOUT` |
| `job.defaultMaxRetries` | 3 | `JOB_DEFAULT_MAX_RETRIES` |
| `reaper.interval` | 60000ms | `REAPER_INTERVAL` |
| `reaper.stalenessThreshold` | 120000ms | `REAPER_STALENESS_THRESHOLD` |

---

> **End of PRD**
>
> This document serves as the complete technical specification for Distro Tasker MVP.  
> All design decisions are justified, all tradeoffs are documented, and the roadmap is execution-ready.
>
> **Next step**: Developer review → approval → begin Week 1.
