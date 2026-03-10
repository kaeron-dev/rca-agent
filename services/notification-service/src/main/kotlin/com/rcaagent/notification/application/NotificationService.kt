package com.rcaagent.notification.application

import com.rcaagent.notification.domain.OrderConfirmedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun notifyOrderConfirmed(event: OrderConfirmedEvent) {
        // TODO Fase 5 — reemplazar por email/SMS real
        // Por ahora solo loguea la notificacion
        logger.info(
            "Notificacion enviada — orderId: {}, productId: {}, cantidad: {}, monto: {}",
            event.orderId,
            event.productId,
            event.quantity,
            event.amount
        )
    }
}
