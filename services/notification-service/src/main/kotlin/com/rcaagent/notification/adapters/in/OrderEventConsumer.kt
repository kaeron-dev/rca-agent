package com.rcaagent.notification.adapters.`in`

import com.rcaagent.notification.application.NotificationService
import com.rcaagent.notification.domain.OrderConfirmedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Consumidor del topic "order.confirmed".
 *
 * Kafka entrega el evento como JSON. Spring lo deserializa
 * automaticamente a OrderConfirmedEvent.
 *
 * El offset se avanza automaticamente despues de procesar
 * el mensaje sin errores. Si el servicio cae, cuando vuelve
 * retoma desde el ultimo offset confirmado.
 *
 * Topic:     order.confirmed
 * Publicado: order-service cuando status = CONFIRMED
 * Grupo:     notification-group (cada instancia del servicio
 *            comparte el grupo — Kafka balancea los mensajes
 *            entre instancias del mismo grupo)
 */
@Component
class OrderEventConsumer(
    private val notificationService: NotificationService
) {

    @KafkaListener(
        topics = ["order.confirmed"],
        groupId = "notification-group"
    )
    fun onOrderConfirmed(event: OrderConfirmedEvent) {
        notificationService.notifyOrderConfirmed(event)
    }
}
