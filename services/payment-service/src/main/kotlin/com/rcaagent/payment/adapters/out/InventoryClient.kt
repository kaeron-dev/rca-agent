package com.rcaagent.payment.adapters.out

import com.rcaagent.payment.ports.out.InventoryPort
import com.rcaagent.payment.ports.out.InventoryResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

data class InventoryRequest(val productId: String, val quantity: Int)

@Component
class InventoryClient(
    private val webClient: WebClient,
    @Value("\${services.inventory.url}") private val inventoryUrl: String
) : InventoryPort {

    override suspend fun reserveInventory(productId: String, quantity: Int): InventoryResponse {
        return webClient.post()
            .uri("$inventoryUrl/inventory/reserve")
            .bodyValue(InventoryRequest(productId, quantity))
            .retrieve()
            .awaitBody()
    }
}
