#!/bin/bash
mkdir -p infra/otel-agent
JAR="infra/otel-agent/opentelemetry-javaagent.jar"
if [ ! -f "$JAR" ]; then
  echo "Descargando OTel Java Agent v2.9.0..."
  curl -L -o "$JAR" \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.9.0/opentelemetry-javaagent.jar
  echo "Listo → $JAR"
else
  echo "OTel agent ya existe → $JAR"
fi
