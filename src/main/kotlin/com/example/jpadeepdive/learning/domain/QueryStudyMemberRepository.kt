package com.example.jpadeepdive.learning.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface QueryStudyMemberRepository :
    JpaRepository<QueryStudyMember, Long>, JpaSpecificationExecutor<QueryStudyMember> {

    // findById는 상속받은 기본 구현 → EntityManager.find → @Id인 memberId 조회.

    // 아래 세 메서드는 이름을 파싱해 Criteria 쿼리를 만드는 Derived Query.
    fun findByName(name: String): QueryStudyMember?

    fun findByMemberId(memberId: Long): QueryStudyMember?

    // Member는 설명 토큰. By 뒤의 Id는 일반 속성 id를 가리킨다.
    fun findMemberById(id: Long): QueryStudyMember?

    @Query("select m from QueryStudyMember m where m.memberId = :memberId")
    fun findUsingJpql(@Param("memberId") memberId: Long): QueryStudyMember

    @Query("select m.name from QueryStudyMember m where m.memberId = :memberId")
    fun findNameUsingJpql(@Param("memberId") memberId: Long): String

    // 벌크 DML은 기존 관리 객체의 필드를 수정해 주지 않는다.
    @Modifying
    @Query("update QueryStudyMember m set m.name = :name where m.memberId = :memberId")
    fun renameInBulk(@Param("memberId") memberId: Long, @Param("name") name: String): Int
}
