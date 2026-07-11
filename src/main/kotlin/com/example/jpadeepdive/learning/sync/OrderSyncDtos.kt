package com.example.jpadeepdive.learning.sync

import com.example.jpadeepdive.learning.domain.Member
import com.example.jpadeepdive.learning.domain.PurchaseOrder
import java.math.BigDecimal

data class IncomingOrderBatch(
    val memberUid: String,
    val lines: List<IncomingOrderLine>,
)

data class PreparedOrderBatch(
    val lines: List<IncomingOrderLine>,
)

data class IncomingOrderLine(
    val orderCode: String,
    val productName: String,
    val amount: BigDecimal,
    val status: String,
) {
    lateinit var member: Member
        private set

    fun attachMember(member: Member): IncomingOrderLine {
        this.member = member
        return this
    }

    fun matches(order: PurchaseOrder): Boolean {
        return orderCode == order.orderCode
    }

    fun applyTo(order: PurchaseOrder) {
        order.applyChanges(this)
        order.replaceMemberReference(member)
    }

    fun toNewEntity(): PurchaseOrder {
        return PurchaseOrder(
            member = member,
            orderCode = orderCode,
            productName = productName,
            amount = amount,
            status = status,
        )
    }
}

data class OrderPublishMessage(
    val orderCode: String,
    val productName: String,
    val amount: BigDecimal,
    val status: String,
) {
    var parent: MemberPublishMessage? = null
}

data class MemberPublishMessage(
    val uid: String,
    val name: String,
) {
    var parent: OrganizationPublishMessage? = null
}

data class OrganizationPublishMessage(
    val code: String,
    val name: String,
)
