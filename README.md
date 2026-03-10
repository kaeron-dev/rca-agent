# RCA Agent

AI agent that automates Root Cause Analysis on distributed traces.
Receives a `trace_id`, analyzes the complete span tree via Grafana Tempo,
correlates with Prometheus metrics, and produces structured RCA reports
using LangChain4j with Gemini or Ollama.

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Servicios base | Kotlin 2.0 + Spring Boot 3.3 |
| Agente IA | Java 21 + LangChain4j |
| Trazas | OpenTelemetry + Grafana Tempo |
| Métricas | Prometheus + Grafana |
| LLM local | Ollama + Llama 3.2 |
| LLM API | Gemini (gratis, sin tarjeta) |

---

## Prerequisitos

- Docker Engine 27.x
- Java 21 JDK (Eclipse Temurin)

---

## Setup
```bash
cp .env.example .env
docker compose up --build
```

---

## Servicios

### order-service — `localhost:8081`

Expone un endpoint REST para crear órdenes. Recibe el `productId` y la
`quantity`, crea una orden con un ID único, coordina el cobro con
`payment-service` y la reserva de stock con `inventory-service`.
Cuando ambos confirman, devuelve la orden con status `CONFIRMED`.
Si alguno falla, devuelve `FAILED`.

**Arquitectura:** Hexagonal (Ports & Adapters)
- `domain/` — `Order`, `OrderStatus` — lógica pura de negocio
- `ports/in/` — `CreateOrderUseCase` — contrato de entrada
- `ports/out/` — `PaymentPort`, `InventoryPort` — contratos de salida
- `adapters/in/` — `OrderController` — recibe HTTP
- `adapters/out/` — `PaymentClient`, `InventoryClient` — llama a otros servicios

**Ejemplo:**
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "p1", "quantity": 2}'

# Response
{
  "orderId": "a1b2c3d4-...",
  "status": "CONFIRMED"
}
```

**Pendiente Fase 4:** Circuit Breaker, Retry, Timeout con Resilience4j.

---

## Modos de ejecución

| RAM | Modo | Configuración |
|-----|------|--------------|
| 8 GB | Gemini | `LLM_MODE=gemini` en `.env` |
| 16 GB+ | Ollama | `LLM_MODE=ollama` en `.env` |

---

## Ver el agente en acción
```bash
./scripts/demo.sh
```
