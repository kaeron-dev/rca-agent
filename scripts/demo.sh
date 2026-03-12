#!/bin/bash
# demo.sh — injects latency, fires a request, captures trace_id, calls the RCA agent
# Usage: ./scripts/demo.sh

set -e

ORDER_SERVICE="http://localhost:8081"
RCA_AGENT="http://localhost:8080"
PAYMENT_SERVICE="http://localhost:8082"

echo "═══════════════════════════════════════════════════"
echo "  RCA Agent Demo"
echo "═══════════════════════════════════════════════════"

# 1. Inject artificial latency into payment-service
echo ""
echo "▶ Step 1: Injecting 3000ms latency into payment-service..."
curl -s -X POST "$PAYMENT_SERVICE/actuator/demo/latency" \
  -H "Content-Type: application/json" \
  -d '{"latencyMs": 3000}' > /dev/null
echo "  ✓ Latency injected"

# 2. Fire a request to order-service
echo ""
echo "▶ Step 2: Firing request to order-service..."
RESPONSE=$(curl -s -i -X POST "$ORDER_SERVICE/orders" \
  -H "Content-Type: application/json" \
  -d '{"productId": "p1", "quantity": 2}')

# 3. Extract trace_id from response header
TRACE_ID=$(echo "$RESPONSE" | grep -i "x-trace-id" | awk '{print $2}' | tr -d '\r')

if [ -z "$TRACE_ID" ]; then
  echo "  ✗ Could not extract trace_id from response headers"
  echo "  Hint: make sure all services are healthy: docker compose ps"
  exit 1
fi

echo "  ✓ trace_id captured: $TRACE_ID"

# 4. Wait for trace to be ingested by Tempo
echo ""
echo "▶ Step 3: Waiting 3s for Tempo to ingest the trace..."
sleep 3

# 5. Call the RCA agent
echo ""
echo "▶ Step 4: Calling RCA agent..."
echo ""
RCA_REPORT=$(curl -s -X POST "$RCA_AGENT/api/analyze/$TRACE_ID" \
  -H "Content-Type: application/json")

echo "$RCA_REPORT" | python3 -m json.tool 2>/dev/null || echo "$RCA_REPORT"

# 6. Reset latency
echo ""
echo "▶ Step 5: Resetting latency..."
curl -s -X POST "$PAYMENT_SERVICE/actuator/demo/latency" \
  -H "Content-Type: application/json" \
  -d '{"latencyMs": 0}' > /dev/null
echo "  ✓ Latency reset to 0ms"

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Demo complete"
echo "═══════════════════════════════════════════════════"
