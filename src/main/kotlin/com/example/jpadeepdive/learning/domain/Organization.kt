package com.example.jpadeepdive.learning.domain

import com.example.jpadeepdive.learning.sync.OrganizationPublishMessage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "organizations")
class Organization(
    @Column(name = "code", nullable = false, length = 100, unique = true)
    var code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0L
        protected set

    fun toPublishMessage(): OrganizationPublishMessage {
        return OrganizationPublishMessage(
            code = code,
            name = name,
        )
    }
}
