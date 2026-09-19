package com.example.jpadeepdive.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

// Spring Boot 기본 패키지 스캔으로 등록하는 학습용 모델.
@Entity
@Table(name = "query_study_members")
class QueryStudyMember(
    // 직접 할당: IDENTITY의 즉시 INSERT가 flush 실험을 가리지 않게 한다.
    @Id
    var memberId: Long,

    @Column(name = "business_id", nullable = false, unique = true)
    var id: Long,

    @Column(nullable = false, unique = true)
    var name: String,
)

@Entity
@Table(name = "query_study_notes")
class QueryStudyNote(
    @Id
    var noteId: Long,
    var title: String,
)
