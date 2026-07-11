package com.example.jpadeepdive.learning.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationRepository : JpaRepository<Organization, Long> {
    fun findByCode(code: String): Organization?
}
