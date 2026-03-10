package com.rcaagent.payment.application

/**
 * Estado compartido para configuracion de demo.
 *
 * LIMITACION ACTUAL (Fase 1):
 * @Volatile garantiza visibilidad entre coroutines del mismo proceso JVM.
 * NO funciona entre multiples pods — cada pod tiene su propia JVM.
 *
 * SOLUCION PARA PRODUCCION (Fase 5 — Kubernetes):
 * Reemplazar por Redis como estado compartido entre pods:
 *   - LatencyController escribe en Redis key="demo:latency:payment-service"
 *   - PaymentService lee de Redis antes de procesar el pago
 *   - Todos los pods leen el mismo Redis — comportamiento consistente
 */
object DemoConfig {
    @Volatile
    var latencyMs: Long = 0
}
