package com.example.jpadeepdive.learning.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, Long> {
    @Query(
        """
        select o
        from PurchaseOrder o
        where o.member.id = :memberId
        """,
    )
    fun findAllByMemberId(@Param("memberId") memberId: Long): List<PurchaseOrder>

    @Query(
        """
        select o
        from PurchaseOrder o
        join fetch o.member m
        join fetch m.organization
        where o.member.id = :memberId
        """,
    )
    fun findAllByMemberIdWithMemberAndOrganization(@Param("memberId") memberId: Long): List<PurchaseOrder>
}
