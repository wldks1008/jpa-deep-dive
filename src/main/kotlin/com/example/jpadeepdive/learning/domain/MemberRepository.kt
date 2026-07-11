package com.example.jpadeepdive.learning.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByUid(uid: String): Member?

    @Query(
        """
        select m
        from Member m
        join fetch m.organization
        where m.uid = :uid
        """,
    )
    fun findByUidWithOrganization(@Param("uid") uid: String): Member?
}
