package com.example.jpadeepdive.learning.sync

import com.example.jpadeepdive.learning.domain.MemberRepository
import com.example.jpadeepdive.learning.domain.PurchaseOrder
import com.example.jpadeepdive.learning.domain.PurchaseOrderRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement
import java.time.LocalDateTime

@Service
class OrderSyncService(
    private val memberRepository: MemberRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun findOrdersWithoutFetchJoin(memberId: Long): List<PurchaseOrder> {
        return purchaseOrderRepository.findAllByMemberId(memberId)
    }

    @Transactional(readOnly = true)
    fun findOrdersWithMemberAndOrganization(memberId: Long): List<PurchaseOrder> {
        return purchaseOrderRepository.findAllByMemberIdWithMemberAndOrganization(memberId)
    }

    @Transactional(readOnly = true)
    fun prepareBatch(command: IncomingOrderBatch): PreparedOrderBatch {
        val member = memberRepository.findByUidWithOrganization(command.memberUid)
            ?: throw IllegalArgumentException("member not found: ${command.memberUid}")

        return PreparedOrderBatch(
            lines = command.lines.map { it.attachMember(member) },
        )
    }

    @Transactional
    fun synchronize(batch: PreparedOrderBatch): List<PurchaseOrder> {
        val lines = batch.lines.distinctBy { it.orderCode }
        if (lines.isEmpty()) return emptyList()

        val member = lines.first().member
        val existingOrders = purchaseOrderRepository.findAllByMemberId(member.id)
            .filter { order -> lines.any { it.matches(order) } }

        val newOrders = mutableListOf<PurchaseOrder>()
        for (line in lines) {
            existingOrders.find { line.matches(it) }
                ?.let { line.applyTo(it) }
                ?: newOrders.add(line.toNewEntity())
        }

        if (newOrders.isEmpty()) return existingOrders

        purchaseOrderRepository.saveAll(newOrders)
        return findRequestedOrders(member.id, lines.map { it.orderCode })
            .onEach { it.replaceMemberReference(member) }
    }

    @Transactional
    fun synchronizeWithoutRequestFiltering(batch: PreparedOrderBatch): List<PurchaseOrder> {
        val lines = batch.lines.distinctBy { it.orderCode }
        if (lines.isEmpty()) return emptyList()

        val member = lines.first().member
        val existingOrders = purchaseOrderRepository.findAllByMemberId(member.id)

        for (line in lines) {
            existingOrders.find { line.matches(it) }
                ?.let { line.applyTo(it) }
        }

        return existingOrders
    }

    @Transactional
    fun synchronizeUsingBulk(batch: PreparedOrderBatch): List<PurchaseOrder> {
        val lines = batch.lines.distinctBy { it.orderCode }
        if (lines.isEmpty()) return emptyList()

        val member = lines.first().member
        val existingOrders = purchaseOrderRepository.findAllByMemberId(member.id)
            .filter { order -> lines.any { it.matches(order) } }
        val newLines = mutableListOf<IncomingOrderLine>()

        for (line in lines) {
            existingOrders.find { line.matches(it) }
                ?.let { line.applyTo(it) }
                ?: newLines.add(line)
        }

        if (newLines.isNotEmpty()) {
            bulkInsert(member.id, newLines)
        }

        return findRequestedOrders(member.id, lines.map { it.orderCode })
            .onEach { it.replaceMemberReference(member) }
    }

    @Transactional
    fun synchronizeUsingBulkWithoutReferenceReplacement(batch: PreparedOrderBatch): List<PurchaseOrder> {
        val lines = batch.lines.distinctBy { it.orderCode }
        if (lines.isEmpty()) return emptyList()

        val member = lines.first().member
        val existingOrders = purchaseOrderRepository.findAllByMemberId(member.id)
            .filter { order -> lines.any { it.matches(order) } }
        val newLines = mutableListOf<IncomingOrderLine>()

        for (line in lines) {
            existingOrders.find { line.matches(it) }
                ?.let { line.applyTo(it) }
                ?: newLines.add(line)
        }

        if (newLines.isNotEmpty()) {
            bulkInsert(member.id, newLines)
        }

        return findRequestedOrders(member.id, lines.map { it.orderCode })
    }

    @Transactional
    fun changeStatusByDirtyChecking(orderId: Long, status: String) {
        val order = purchaseOrderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("order not found: $orderId") }
        order.changeStatus(status)
    }

    private fun findRequestedOrders(memberId: Long, orderCodes: List<String>): List<PurchaseOrder> {
        val orderCodeSet = orderCodes.toSet()
        return purchaseOrderRepository.findAllByMemberId(memberId)
            .filter { it.orderCode in orderCodeSet }
    }

    private fun bulkInsert(memberId: Long, lines: List<IncomingOrderLine>) {
        val now = LocalDateTime.now()
        jdbcTemplate.batchUpdate(
            """
            insert into orders (member_id, order_code, product_name, amount, status, updated_at)
            values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            lines,
            lines.size,
        ) { statement: PreparedStatement, line: IncomingOrderLine ->
            statement.setLong(1, memberId)
            statement.setString(2, line.orderCode)
            statement.setString(3, line.productName)
            statement.setBigDecimal(4, line.amount)
            statement.setString(5, line.status)
            statement.setObject(6, now)
        }
    }
}
