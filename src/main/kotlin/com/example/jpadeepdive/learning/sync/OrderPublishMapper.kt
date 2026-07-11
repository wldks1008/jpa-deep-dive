package com.example.jpadeepdive.learning.sync

import com.example.jpadeepdive.learning.domain.PurchaseOrder
import org.springframework.stereotype.Component

@Component
class OrderPublishMapper {
    fun mapForPublish(orders: List<PurchaseOrder>): List<OrderPublishMessage> {
        return orders.flatMap { it.toPublishMessages() }
    }
}
