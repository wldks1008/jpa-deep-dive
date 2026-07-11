package com.example.jpadeepdive.learning.domain

import com.example.jpadeepdive.learning.sync.MemberPublishMessage
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

@Entity
@Table(name = "members")
class Member(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "organization_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    var organization: Organization,

    @Column(name = "uid", nullable = false, length = 100, unique = true)
    var uid: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0L
        protected set

    fun replaceOrganizationReference(organization: Organization) {
        this.organization = organization
    }

    fun toPublishMessage(): MemberPublishMessage {
        return MemberPublishMessage(
            uid = uid,
            name = name,
        ).apply {
            parent = organization.toPublishMessage()
        }
    }
}
