# RCA Agent

> AI agent that automates Root Cause Analysis on distributed traces.
> Receives a `trace_id`, fetches the complete span tree from Grafana Tempo,
> correlates with Prometheus metrics, compares against historical baseline,
> and produces structured RCA reports using LangChain4j with Gemini or Ollama.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-2.9.0-blue)](https://opentelemetry.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## What it does

Instead of manually inspecting traces in Grafana, you send a single request:

```bash
curl -X GET http://localhost:8080/api/analyze/4bf92f3577b34da6a3ce929d0e0e4736
```

And get back a structured report:

```json
{
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "rootCause": "Slow db.query in payment-service — SELECT payments",
  "anomalySpan": "db.query",
  "durationMs": 3980,
  "baselineMs": 45,
  "anomalyFactor": 88.4,
  "recommendation": "Run ANALYZE payments; or add index on orderId column",
  "confidence": 0.94
}
```

The agent fetches the span tree from Tempo, compares against the Prometheus baseline,
and sends the enriched context to an LLM (Ollama locally or Gemini via API).

---

## Architecture

### Full data flow

```
Kotlin Services          OTel Collector         Backends
order-service   ──┐
payment-service ──┼──► :4318 HTTP ──► Tempo   :4317 (traces)
inventory-service─┤                  └──► Prometheus :9090 (metrics)
notification-svc──┘

                                    RCA Agent (Java 21)
User ──► GET /api/analyze/{trace_id} ──► queries Tempo + Prometheus
                                     ──► sends context to LLM
                                     ──► returns RCA Report
```

### Hexagonal Architecture (RCA Agent)

The agent follows strict Hexagonal (Ports & Adapters) — the domain never imports adapters.

```
adapters/in/
  AnalyzeController.java       ← REST endpoint

domain/
  SpanTree.java                ← core entities
  RcaReport.java
  TraceAnalysisService.java    ← use case implementation

ports/in/
  AnalyzeTraceUseCase.java     ← interface (what domain offers)

ports/out/
  TraceRepository.java         ← interface (what domain needs)
  MetricsRepository.java
  RcaAnalyzer.java

adapters/out/
  TempoTraceAdapter.java       ← implements TraceRepository
  PrometheusAdapter.java       ← implements MetricsRepository
  LangChain4jRcaAdapter.java   ← implements RcaAnalyzer
```

Dependencies always point inward. `TraceAnalysisService` depends on
`TraceRepository` (interface), never on `TempoTraceAdapter` (implementation).

### Kafka flow

```
order-service                    Kafka                notification-service
  publishes ──► order.confirmed ──► partition 0 ──► @KafkaListener
                                    partition 1       OrderEventConsumer
                                    partition 2       NotificationService
```

order-service publishes without knowing who listens.
notification-service listens without knowing who publishes.

---

## Stack

| Component | Technology | Version |
|---|---|---|
| RCA Agent | Java 21 + LangChain4j | 21 LTS |
| Microservices | Kotlin + Spring Boot | 2.0.21 / 3.3.5 |
| Messaging | Apache Kafka (KRaft) | 7.7.0 |
| Tracing | Grafana Tempo | 2.6.1 |
| Metrics | Prometheus | 2.54.1 |
| Dashboards | Grafana | 11.3.0 |
| OTel Pipeline | OTel Collector + Java Agent | 0.111.0 / 2.9.0 |
| LLM (local) | Ollama / Llama 3.2 | latest |
| LLM (cloud) | Google Gemini API | — |
| Build | Gradle Kotlin DSL | 8.10.x |

---

## Monorepo structure

```
rca-agent/
├── Dockerfile                      ← single multi-stage Dockerfile for all services
├── docker-compose.yml              ← full local stack
├── build.gradle.kts                ← root Gradle config
├── settings.gradle.kts             ← monorepo module declarations
├── .env.example                    ← environment variable reference
├── scripts/
│   ├── demo.sh                     ← end-to-end demo script
│   └── download-otel-agent.sh      ← downloads OTel Java Agent jar
├── services/
│   ├── order-service/              ← Kotlin :8081
│   ├── payment-service/            ← Kotlin :8082
│   ├── inventory-service/          ← Kotlin :8083
│   └── notification-service/       ← Kotlin :8084 (Kafka consumer)
├── agent/                          ← Java 21 RCA Agent :8080
└── infra/
    ├── otel-collector-config.yml
    ├── prometheus.yml
    ├── tempo.yml
    └── grafana/provisioning/
```

---

## Prerequisites

- Docker + Docker Compose
- Java 21 (for local development without Docker)
- Git with GPG signing (optional but recommended)

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/kaeron-dev/rca-agent.git
cd rca-agent
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` and set the required values:

```bash
# LLM mode — choose "local" (Ollama) or "cloud" (Gemini)
LLM_MODE=local

# Required only if LLM_MODE=cloud
GEMINI_API_KEY=your_key_here

# Leave defaults for local development
TEMPO_URL=http://localhost:3200
PROMETHEUS_URL=http://localhost:9090
OLLAMA_URL=http://localhost:11434
```

### 3. Download the OTel Java Agent

The agent jar instruments all Kotlin services automatically via bytecode —
no code changes required.

```bash
./scripts/download-otel-agent.sh
```

Expected output:
```
Downloading OTel Java Agent v2.9.0...
Listo → infra/otel-agent/opentelemetry-javaagent.jar
```

### 4. Build and start the stack

```bash
docker compose build
docker compose up -d
```

Wait for all containers to be healthy:

```bash
docker compose ps
```

Expected — all 10 containers in `Up` or `Up (healthy)` status:

```
NAME                   STATUS
grafana                Up
inventory-service      Up
kafka                  Up (healthy)
notification-service   Up
ollama                 Up
order-service          Up
otel-collector         Up (healthy)
payment-service        Up
prometheus             Up (healthy)
tempo                  Up (healthy)
```

### 5. Verify the stack

Open Grafana at [http://localhost:3000](http://localhost:3000) — credentials `admin / admin`.

---

## Generating traces

Send a request to order-service to generate a real distributed trace:

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-123",
    "productId": "product-456",
    "amount": 99.99
  }'
```

This triggers a span tree across three services:

```
order-service  (120ms)
  ├── payment-service  (80ms)
  │     └── inventory-service  (40ms)
  └── inventory-service  (35ms)
```

### Injecting latency for demo purposes

Simulate a slow payment service (4200ms artificial latency):

```bash
curl -X POST "http://localhost:8082/demo/latency?ms=4200"
```

Reset:

```bash
curl -X POST http://localhost:8082/demo/latency/reset
```

---

## Viewing traces in Grafana Tempo

1. Open [http://localhost:3000](http://localhost:3000)
2. Navigate to **Explore**
3. Select datasource → **Tempo**
4. Query type → **Search**
5. Service name → `order-service`
6. Click **Run query**

You should see traces with spans from `order-service`, `payment-service`,
and `inventory-service` with real durations.

---

## Design decisions

### Why Kotlin for services and Java 21 for the agent?

Kotlin wins clearly for microservices: data classes eliminate boilerplate,
coroutines enable parallel calls without blocking threads, and null safety
catches issues at compile time.

Java 21 was a deliberate choice for the agent — to learn modern JVM features
(Records, Sealed Classes, Virtual Threads) by writing real production code,
not tutorials. LangChain4j, the most mature JVM agent framework, is also Java-first.

### Why Hexagonal Architecture?

Separates the domain from the outside world via ports (interfaces) and
adapters (implementations). Dependencies always point inward.

If Tempo is replaced by Jaeger tomorrow, only `TempoTraceAdapter` changes —
`TraceAnalysisService` never knows the difference.

### Why Kafka instead of direct HTTP for notifications?

order-service publishes `order.confirmed` without knowing who listens.
notification-service listens without knowing who publishes. Total decoupling.

If notification-service is down, Kafka holds the messages. When it recovers,
it processes from the last committed offset — no lost notifications.

### LLM Fallback Chain

The agent uses Chain of Responsibility to handle LLM failures:

```
Ollama (local) → Gemini (API) → degraded response (partial analysis)
```

The system always returns something useful, even when all LLMs are unavailable.
Adding a new LLM provider requires only a new handler — the adapter never changes.

### @Volatile vs Redis for demo state

`DemoConfig.latencyMs` uses `@Volatile` for JVM-level visibility within a single
process. This works for single-instance demos. For multi-pod deployments, Redis
is required for shared state across instances (Phase 5).

---

## Production considerations

This is a Phase 1 demo. The following patterns are designed for but not yet
implemented — they are planned for Phase 5:

| Pattern | Service | Purpose |
|---|---|---|
| Outbox Pattern | order-service | Kafka events survive if Kafka is down |
| Idempotency Key | payment-service | Prevent duplicate charges on retry |
| Redis shared state | payment-service | DemoConfig across multiple pods |
| PostgreSQL | rca-agent | Persistent baseline history |
| Circuit Breaker | all adapters/out | Resilience4j — fast fail on Tempo/LLM |
| SELECT FOR UPDATE | inventory-service | Race condition on stock reservation |

---

## Phase roadmap

| Phase | Title | Goal |
|---|---|---|
| **1** ✅ | Monorepo + Local Stack | `docker compose up` generates real traces |
| **2** | Domain + Hexagonal Ports | Agent architecture defined |
| **3** | Adapters + LangChain4j | Agent connected to Tempo, Prometheus, LLM |
| **4** | Resilience + CAP | Agent survives failures with Circuit Breaker |
| **5** | Evaluation + Benchmark | Agent evaluated against real anomalies |
| **6** | Polish + Portfolio | Repo ready for technical interviews |

---

## Local development (without Docker)

Run individual services locally:

```bash
# Start infrastructure only
docker compose up -d tempo prometheus grafana otel-collector kafka

# Run a service locally
./gradlew :services:order-service:bootRun
```

---

## License

MIT — see [LICENSE](LICENSE)
---

## CAP Theorem — decisions per component

In a distributed system you can only guarantee two of three: Consistency, Availability, Partition tolerance. Every external dependency in this project makes a different trade-off.

| Component | CAP choice | Behavior under partition |
|---|---|---|
| Grafana Tempo | **CP** | Prefers unavailability over returning incomplete traces. If partitioned, Tempo does not respond rather than returning a partial span tree. The agent detects this via Circuit Breaker and returns a degraded response immediately. |
| Prometheus | **AP** | Prefers availability over consistency. Under partition, Prometheus returns the metrics it has — potentially stale. The agent uses them with a warning in the RCA report rather than blocking analysis. |
| H2 (baseline store) | **CP** | In-memory, single node. No partition scenario in Phase 1 — replaced by PostgreSQL in Phase 5 for persistence across restarts. |
| Kafka | **AP** | Producers continue publishing to available brokers. Consumers resume from last committed offset on recovery. No messages are lost — notification-service replays on restart. |
| RCA Agent | **AP** | Designed to always return something useful. A partial analysis with `confidence: 0.0` is more useful than an HTTP 500. The fallback chain ensures the system never throws to the caller. |

### Why Tempo is CP and Prometheus is AP

Tempo stores traces as immutable append-only data. A partial trace — missing spans from one service — produces a misleading RCA. It is safer to return nothing and let the Circuit Breaker handle recovery.

Prometheus scrapes metrics on a pull model with configurable intervals. A metric value from 15 seconds ago is still useful for anomaly correlation. Returning stale data with a warning is better than blocking the analysis pipeline.

---

## Resilience patterns — implementation decisions

### Circuit Breaker on Tempo
```
Tempo down → 3 consecutive failures → circuit OPENS
             → subsequent calls fail immediately (no thread blocking)
             → after 30s → circuit transitions to HALF-OPEN
             → 3 probe calls → if successful → circuit CLOSES
```

**Why 30s wait duration:** Tempo restart in Docker takes ~15-20s. A 30s window ensures the service is fully healthy before probing. Shorter windows cause thundering herd on recovery.

**Why sliding window of 10:** Avoids opening the circuit on isolated transient failures. 10 calls with 50% failure rate means 5 consecutive failures — enough signal to distinguish a real outage from network noise.

### Retry with exponential backoff on LLM calls
```
LLM call fails → wait 1s → retry
                → wait 2s → retry
                → wait 4s → retry
                → all attempts exhausted → fallback method called
```

**Why exponential and not fixed:** A struggling LLM under load needs time to recover. Fixed 1s retries add load to an already saturated model. Doubling the wait gives the LLM breathing room between attempts.

**Why max 3 attempts:** At 1s + 2s + 4s = 7s total wait plus the original call, the user is already waiting ~8-10s. Beyond 3 retries the degraded fallback response is more useful than continued waiting.

### Bulkhead on LLM calls
```
Max 5 concurrent LLM calls
Requests beyond 5 → wait up to 2s for a slot
                  → if no slot available → fallback method called
```

**Why 5 concurrent:** LLMs — especially local Ollama — are CPU/GPU bound. More than 5 concurrent requests causes context switching overhead that makes all requests slower. Better to queue or degrade than to let all requests time out together.

**Why 2s wait:** A request that waits 2s for a slot and then gets processed is better than a request that times out after 60s. The 2s window handles short bursts without immediately degrading.

### Fallback chain
```
Primary LLM (Ollama/Gemini) fails
  → Retry 3x with backoff
    → All retries exhausted OR bulkhead full
      → fallback() called
        → Returns RcaReport with:
            confidence: 0.0          ← signals degraded to caller
            rootCause: span data      ← preserves what we know
            recommendation: manual    ← actionable even without LLM
```

**Why confidence 0.0 and not an exception:** The controller returns 200 with a degraded report rather than 503. The caller — whether a human or an automated system — can decide how to handle low confidence. An exception gives the caller no information. A degraded report gives them the span data to start manual investigation.

### Timeout configuration

| Dependency | Timeout | Rationale |
|---|---|---|
| Tempo | 5s | Trace fetch is a simple HTTP GET. If Tempo takes more than 5s it is either overloaded or down — both warrant Circuit Breaker. |
| LLM (fast model) | 30s | Gemini Flash and small Ollama models respond in 3-8s under normal load. 30s accommodates model cold starts. |
| LLM (premium model) | 60s | Gemini Pro and large models can take 15-30s for complex traces. 60s is the practical upper bound before user experience degrades. |

---
