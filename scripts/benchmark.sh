#!/bin/bash
# benchmark.sh — runs the evaluation dataset through the RCA agent
# and produces a markdown accuracy report.
#
# Modes:
#   ./scripts/benchmark.sh           — calls /api/evaluate/md (agent handles dataset internally)
#   ./scripts/benchmark.sh --live    — injects each trace via /api/analyze and measures accuracy live
#
# Output:
#   benchmark/report_<timestamp>.md  — accuracy report
#   benchmark/latest.md              — symlink to latest report

set -e

MODE=${1:-}
RCA_AGENT="http://localhost:8080"
REPORT_DIR="benchmark"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_FILE="$REPORT_DIR/report_$TIMESTAMP.md"
LATEST_LINK="$REPORT_DIR/latest.md"

mkdir -p "$REPORT_DIR"

echo "═══════════════════════════════════════════════════"
echo "  RCA Agent Benchmark — mode: ${MODE:-internal}"
echo "  Output: $REPORT_FILE"
echo "═══════════════════════════════════════════════════"

# ── Check agent is up ────────────────────────────────
echo ""
echo "▶ Checking agent health..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$RCA_AGENT/actuator/health")
if [ "$HEALTH" != "200" ]; then
  echo "  ✗ Agent not healthy (HTTP $HEALTH). Start the stack first:"
  echo "    docker compose up -d"
  exit 1
fi
echo "  ✓ Agent healthy"

if [ "$MODE" = "--live" ]; then
  # ── Live mode: inject each trace individually ────────
  echo ""
  echo "▶ Running live evaluation against /api/analyze..."
  echo ""

  DATASET="agent/src/test/resources/dataset/labeled_traces.json"
  if [ ! -f "$DATASET" ]; then
    echo "  ✗ Dataset not found at $DATASET"
    exit 1
  fi

  TOTAL=0
  CORRECT=0
  declare -A TYPE_TOTAL
  declare -A TYPE_CORRECT

  # Parse dataset with python3 — available in all Linux/Mac environments
  TRACE_IDS=$(python3 -c "
import json, sys
with open('$DATASET') as f:
    data = json.load(f)
for t in data:
    print(t['traceId'] + '|' + t['expectedAnomalyType'] + '|' + t['expectedRootCauseSpan'])
")

  RESULTS=""

  while IFS='|' read -r TRACE_ID EXPECTED_TYPE EXPECTED_SPAN; do
    TOTAL=$((TOTAL + 1))
    TYPE_TOTAL[$EXPECTED_TYPE]=$((${TYPE_TOTAL[$EXPECTED_TYPE]:-0} + 1))

    RESPONSE=$(curl -s -X POST "$RCA_AGENT/api/analyze/$TRACE_ID" 2>/dev/null)
    ACTUAL_TYPE=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('anomalyType','UNKNOWN'))" 2>/dev/null || echo "ERROR")
    CONFIDENCE=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('confidence',0.0))" 2>/dev/null || echo "0.0")

    if [ "$ACTUAL_TYPE" = "$EXPECTED_TYPE" ]; then
      CORRECT=$((CORRECT + 1))
      TYPE_CORRECT[$EXPECTED_TYPE]=$((${TYPE_CORRECT[$EXPECTED_TYPE]:-0} + 1))
      STATUS="✅"
    else
      STATUS="❌"
    fi

    echo "  $STATUS $TRACE_ID — expected: $EXPECTED_TYPE / actual: $ACTUAL_TYPE (conf: $CONFIDENCE)"
    RESULTS="$RESULTS\n| $TRACE_ID | $EXPECTED_TYPE | $ACTUAL_TYPE | $CONFIDENCE | $STATUS |"

  done <<< "$TRACE_IDS"

  # ── Generate markdown report ─────────────────────────
  ACCURACY=$(python3 -c "print(f'{($CORRECT/$TOTAL)*100:.1f}')")

  cat > "$REPORT_FILE" << MDEOF
# RCA Agent — Benchmark Report
**Date:** $(date)
**Mode:** live
**Agent:** $RCA_AGENT

## Summary

| Metric | Value |
|---|---|
| Total traces | $TOTAL |
| Correct | $CORRECT |
| Overall accuracy | ${ACCURACY}% |

## Accuracy by anomaly type

| Anomaly Type | Correct | Total | Accuracy |
|---|---|---|---|
MDEOF

  for TYPE in "${!TYPE_TOTAL[@]}"; do
    T_CORRECT=${TYPE_CORRECT[$TYPE]:-0}
    T_TOTAL=${TYPE_TOTAL[$TYPE]}
    T_ACC=$(python3 -c "print(f'{($T_CORRECT/$T_TOTAL)*100:.1f}')")
    echo "| $TYPE | $T_CORRECT | $T_TOTAL | ${T_ACC}% |" >> "$REPORT_FILE"
  done

  cat >> "$REPORT_FILE" << MDEOF

## Per-trace results

| TraceId | Expected Type | Actual Type | Confidence | Correct |
|---|---|---|---|---|
MDEOF

  echo -e "$RESULTS" >> "$REPORT_FILE"

else
  # ── Internal mode: delegate to /api/evaluate/md ─────
  echo ""
  echo "▶ Running internal evaluation via /api/evaluate/md..."
  REPORT=$(curl -s "$RCA_AGENT/api/evaluate/md")

  if [ -z "$REPORT" ]; then
    echo "  ✗ Empty response from /api/evaluate/md"
    exit 1
  fi

  echo "$REPORT" > "$REPORT_FILE"
fi

# ── Symlink latest ───────────────────────────────────
ln -sf "$(basename "$REPORT_FILE")" "$LATEST_LINK"

echo ""
echo "  ✓ Report saved to $REPORT_FILE"
echo "  ✓ Symlink updated: $LATEST_LINK"
echo ""

# ── Print summary to stdout ──────────────────────────
echo "═══════════════════════════════════════════════════"
head -20 "$REPORT_FILE"
echo "..."
echo "  Full report: $REPORT_FILE"
echo "═══════════════════════════════════════════════════"
