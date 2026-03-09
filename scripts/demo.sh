#!/bin/bash
set -e

echo "═══════════════════════════════════════"
echo "  RCA Agent — Demo"
echo "═══════════════════════════════════════"

# 1. Verificar que el stack esté levantado
echo ""
echo "▶ Verificando servicios..."
curl -sf http://localhost:8081/actuator/health > /dev/null || { echo "✗ order-service no responde"; exit 1; }
curl -sf http://localhost:8080/actuator/health > /dev/null || { echo "✗ rca-agent no responde"; exit 1; }
echo "✓ Servicios OK"

# 2. Inyectar latencia artificial en payment-service
echo ""
echo "▶ Inyectando latencia en payment-service..."
# TODO: implementar en Fase 3

# 3. Disparar un request a order-service
echo ""
echo "▶ Disparando request anómalo..."
RESPONSE=$(curl -sf -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "p1", "quantity": 2}')
echo "✓ Response: $RESPONSE"

# 4. Capturar el trace_id del header
echo ""
echo "▶ Capturando trace_id..."
TRACE_ID=$(curl -si -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "p1", "quantity": 2}' \
  | grep -i "x-trace-id" | awk '{print $2}' | tr -d '\r')
echo "✓ trace_id: $TRACE_ID"

# 5. Llamar al agente RCA
echo ""
echo "▶ Analizando traza con RCA Agent..."
RCA_REPORT=$(curl -sf http://localhost:8080/api/analyze/$TRACE_ID)

# 6. Imprimir el RCA report
echo ""
echo "═══════════════════════════════════════"
echo "  RCA Report"
echo "═══════════════════════════════════════"
echo $RCA_REPORT | python3 -m json.tool
