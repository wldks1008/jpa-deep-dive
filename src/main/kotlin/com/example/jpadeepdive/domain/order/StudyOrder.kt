package com.example.jpadeepdive.domain.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "study_orders")
class StudyOrder(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "order_name", nullable = false, length = 100)
    var orderName: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0L
        protected set
}
