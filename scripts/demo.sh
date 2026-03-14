#!/bin/bash
# demo.sh — anomaly injection, trace capture, RCA analysis
#
# Scenarios:
#   default   — slow payment-service (DATABASE_SLOW_QUERY simulation)
#   errors    — payment errors (HIGH_LATENCY_DOWNSTREAM simulation)
#   cascade   — payment slow + inventory errors (CASCADE_FAILURE simulation)
#
# Usage:
#   ./scripts/demo.sh              → default scenario
#   ./scripts/demo.sh cascade      → cascade failure scenario
#   ./scripts/demo.sh errors       → error injection scenario

set -e

SCENARIO=${1:-default}
ORDER_SERVICE="http://localhost:8081"
RCA_AGENT="http://localhost:8080"
PAYMENT_SERVICE="http://localhost:8082"
INVENTORY_SERVICE="http://localhost:8083"

echo "═══════════════════════════════════════════════════"
echo "  RCA Agent Demo — scenario: $SCENARIO"
echo "═══════════════════════════════════════════════════"

# ── Step 0: Wait for RCA Agent ────────────────────────
echo "▶ Step 0: Waiting for RCA Agent to be ready..."
until curl -s "$RCA_AGENT/actuator/health" | grep -q "UP"; do
  echo -n "."
  sleep 1
done
echo " ✓ Ready"

# ── Step 1: Inject anomaly ────────────────────────────
echo ""
echo "▶ Step 1: Injecting anomaly..."

case $SCENARIO in
  cascade)
    curl -s -X POST "$PAYMENT_SERVICE/demo/latency?ms=3000" > /dev/null
    curl -s -X POST "$INVENTORY_SERVICE/demo/errors?rate=80" > /dev/null
    echo "  ✓ payment-service: 3000ms latency injected"
    echo "  ✓ inventory-service: 80% error rate injected"
    ;;
  errors)
    curl -s -X POST "$PAYMENT_SERVICE/demo/errors?rate=100" > /dev/null
    echo "  ✓ payment-service: 100% error rate injected"
    ;;
  *)
    curl -s -X POST "$PAYMENT_SERVICE/demo/latency?ms=3000" > /dev/null
    echo "  ✓ payment-service: 3000ms latency injected"
    ;;
esac

# ── Step 2: Fire request ──────────────────────────────
echo ""
echo "▶ Step 2: Firing request to order-service..."
RESPONSE=$(curl -s -D - -X POST "$ORDER_SERVICE/orders" \
  -H "Content-Type: application/json" \
  -d '{"productId": "p1", "quantity": 2}')

echo "  Response headers and body:"
echo "$RESPONSE" | head -20

# ── Step 3: Extract trace_id ──────────────────────────
echo ""
TRACE_ID=$(echo "$RESPONSE" | grep "{" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data.get('traceId', ''))" 2>/dev/null)

if [ -z "$TRACE_ID" ]; then
  echo "  ✗ Could not extract trace_id from response JSON."
  exit 1
fi

echo "  ✓ trace_id: $TRACE_ID"

# ── Step 4: Wait for Tempo ingestion (Polling) ───────
echo ""
echo "▶ Step 3: Waiting for Tempo to index trace $TRACE_ID..."
MAX_RETRIES=10
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:3200/api/traces/$TRACE_ID")
  if [ "$STATUS" -eq 200 ]; then
    echo "  ✓ Trace indexed after $RETRY_COUNT retries"
    break
  fi
  RETRY_COUNT=$((RETRY_COUNT+1))
  sleep 0.5
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
  echo "  ⚠ Warning: Trace might not be fully indexed yet, proceeding anyway..."
fi

# ── Step 5: Call RCA agent ────────────────────────────
echo ""
echo "▶ Step 4: Calling RCA agent..."
echo ""
RCA_REPORT=$(curl -s --max-time 60 -X GET "$RCA_AGENT/api/analyze/$TRACE_ID")
echo "$RCA_REPORT" | python3 -m json.tool 2>/dev/null || echo "$RCA_REPORT"

# ── Step 6: Reset all injections ─────────────────────
echo ""
echo "▶ Step 5: Resetting all anomaly injections..."
curl -s -X POST "$PAYMENT_SERVICE/demo/reset" > /dev/null
curl -s -X POST "$INVENTORY_SERVICE/demo/reset" > /dev/null
echo "  ✓ All injections reset"

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Demo complete — scenario: $SCENARIO"
echo "═══════════════════════════════════════════════════"
