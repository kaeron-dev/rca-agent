package com.rcaagent.order.adapters.out

import com.rcaagent.order.ports.out.InventoryPort
import com.rcaagent.order.ports.out.InventoryResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

data class InventoryRequest(val productId: String, val quantity: Int)

@Component
class InventoryClient(private val webClient: WebClient) : InventoryPort {
    override suspend fun reserveInventory(productId: String, quantity: Int): InventoryResponse {
        return webClient.post()
            .uri("http://inventory-service:8083/inventory/reserve")
            .bodyValue(InventoryRequest(productId, quantity))
            .retrieve()
            .awaitBody()
    }
}
