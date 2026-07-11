package com.example.jpadeepdive.learning.domain

import com.example.jpadeepdive.learning.sync.IncomingOrderLine
import com.example.jpadeepdive.learning.sync.OrderPublishMessage
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(name = "PurchaseOrder")
@Table(name = "orders")
class PurchaseOrder(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "member_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    var member: Member,

    @Column(name = "order_code", nullable = false, length = 100)
    var orderCode: String,

    @Column(name = "product_name", nullable = false, length = 100)
    var productName: String,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(name = "status", nullable = false, length = 30)
    var status: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0L
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun replaceMemberReference(member: Member) {
        this.member = member
    }

    fun applyChanges(line: IncomingOrderLine) {
        productName = line.productName
        amount = line.amount
        status = line.status
        updatedAt = LocalDateTime.now()
    }

    fun changeStatus(status: String) {
        this.status = status
        updatedAt = LocalDateTime.now()
    }

    fun toPublishMessages(): List<OrderPublishMessage> {
        return listOf(
            OrderPublishMessage(
                orderCode = orderCode,
                productName = productName,
                amount = amount,
                status = status,
            ).apply {
                parent = member.toPublishMessage()
            },
        )
    }
}
