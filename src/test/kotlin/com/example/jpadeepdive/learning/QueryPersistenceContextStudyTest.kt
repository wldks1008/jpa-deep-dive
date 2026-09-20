package com.example.jpadeepdive.learning

import com.example.jpadeepdive.learning.domain.QueryStudyMember
import com.example.jpadeepdive.learning.domain.QueryStudyMemberRepository
import com.example.jpadeepdive.learning.domain.QueryStudyNote
import com.example.jpadeepdive.support.DataSourceProxyTestConfiguration
import jakarta.persistence.EntityManager
import jakarta.persistence.FlushModeType
import net.ttddyy.dsproxy.QueryCountHolder
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 각 테스트를 실행하기 전에 SELECT/INSERT/UPDATE의 순서와 객체 동일성을 먼저 예상해 보자.
 * @DataJpaTest의 각 테스트는 하나의 트랜잭션/영속성 컨텍스트를 공유하며 종료 시 롤백한다.
 * SQL 횟수는 이 모델 + Hibernate 6.6 + 캐시 비활성화 설정에서의 관찰값이다.
 */
@DataJpaTest(showSql = false, properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
    "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "spring.jpa.properties.hibernate.use_sql_comments=false",
    "logging.level.org.hibernate.SQL=off",
    "logging.level.org.hibernate.orm.jdbc.bind=off",
])
@ActiveProfiles("test")
@Import(DataSourceProxyTestConfiguration::class)
@Execution(ExecutionMode.SAME_THREAD)
class QueryPersistenceContextStudyTest {
    @Autowired private lateinit var repository: QueryStudyMemberRepository
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var queryListener: QueryExecutionListener

    @BeforeEach
    fun prepareDatabaseAndEmptyPersistenceContext() {
        em.flushMode = FlushModeType.AUTO
        // memberId=1과 일반 id=101은 의도적으로 다르게 설정했다.
        em.persist(QueryStudyMember(memberId = 1L, id = 101L, name = "kim"))
        em.flush()
        em.clear()
        // 준비 INSERT는 관찰에서 제외. 모든 실험은 DB에 한 행, 비어 있는 1차 캐시로 시작한다.
        QueryCountHolder.clear()
        clearInvocations(queryListener)
    }

    @AfterEach
    fun printObservedSql(testInfo: TestInfo) {
        println("\n[실험] ${testInfo.displayName}")
        val count = QueryCountHolder.getGrandTotal()
        println("총 ${count.total}회: SELECT=${count.select}, INSERT=${count.insert}, UPDATE=${count.update}, DELETE=${count.delete}")
        executedSql().forEachIndexed { index, statement -> println("${index + 1}. $statement") }
        if (count.total == 0L) println("SQL 없음")
        QueryCountHolder.clear()
        clearInvocations(queryListener)
    }

    @Test
    fun `01 findById를 두 번 호출하면 SELECT 한 번과 동일 객체를 얻는다`() {
        val first = repository.findById(1L).orElseThrow()
        val second = repository.findById(1L).orElseThrow()

        assertSql("select")
        assertSame(first, second) // Kotlin ==는 equals, === 또는 assertSame은 참조 비교.
        assertTrue(em.contains(first))
    }

    @Test
    fun `02 파생 쿼리는 SELECT를 두 번 실행해도 동일한 관리 객체를 반환한다`() {
        val first = assertNotNull(repository.findByName("kim"))
        val second = assertNotNull(repository.findByName("kim"))

        assertSql("select", "select")
        assertSame(first, second)
        assertTrue(em.contains(second))
    }

    @Test
    fun `03 PK 속성으로 만든 파생 쿼리도 findById와 달리 SQL을 실행한다`() {
        val managed = repository.findById(1L).orElseThrow()
        assertSame(managed, repository.findByMemberId(1L))
        assertSame(managed, repository.findByMemberId(1L))

        assertSql("select", "select", "select")
        // PK 조건이라는 사실만으로 EntityManager.find 경로가 되는 것은 아니다.
    }

    @Test
    fun `04 예약 findById는 PK를 찾고 findMemberById는 일반 id 속성을 찾는다`() {
        val byPrimaryKey = repository.findById(1L).orElseThrow()
        val byBusinessId = repository.findMemberById(101L)

        assertSame(byPrimaryKey, byBusinessId)
        assertEquals(101L, byPrimaryKey.id)
        assertTrue(repository.findById(101L).isEmpty) // memberId=101은 없다.
        assertNull(repository.findMemberById(1L)) // 일반 id=1은 없다.
        assertSql("select", "select", "select", "select")
    }

    @Test
    fun `05 명시적 JPQL도 SELECT를 실행한 뒤 기존 관리 객체를 반환한다`() {
        val managed = repository.findById(1L).orElseThrow()

        assertSame(managed, repository.findUsingJpql(1L))
        assertSql("select", "select")
    }

    @Test
    fun `06 findAll과 Specification도 DB 조회와 영속성 컨텍스트 관리를 함께 한다`() {
        val managed = repository.findById(1L).orElseThrow()
        val byName = Specification<QueryStudyMember> { root, _, cb ->
            cb.equal(root.get<String>("name"), "kim")
        }

        assertSame(managed, repository.findAll().single())
        assertSame(managed, repository.findAll(byName).single())
        assertSql("select", "select", "select")
    }

    @Test
    fun `07 기본 메서드 existsById와 단순 PK findAllById도 SQL을 실행한다`() {
        val managed = repository.findById(1L).orElseThrow()

        assertTrue(repository.existsById(1L))
        assertSame(managed, repository.findAllById(listOf(1L)).single())
        assertSql("select", "select", "select")
        // 기본 메서드라고 모두 1차 캐시만으로 처리하는 것은 아니다.
    }

    @Test
    fun `08 persist 직후 findById는 INSERT 없이 새 관리 객체를 찾는다`() {
        val newcomer = QueryStudyMember(2L, 102L, "lee") // 비영속 상태
        em.persist(newcomer) // 영속 상태. 아직 DB에 INSERT되지 않았다. (영속성 컨텍스트에만 존재)

        assertSame(newcomer, repository.findById(2L).orElseThrow()) // 영속성 엔티티에서 조회
        assertTrue(em.contains(newcomer))
        assertSql()
        assertEquals(0L, databaseCount(2L))
        // JDBC로 같은 트랜잭션의 DB를 직접 확인. 이 읽기는 JPA 자동 flush를 유발하지 않는다.
        assertSql("select") // datasource-proxy는 위 JDBC 확인용 SELECT도 집계한다.
    }

    @Test
    fun `09 AUTO는 새 엔티티를 조건으로 조회하기 전에 INSERT한다`() {
        val newcomer = QueryStudyMember(2L, 102L, "lee")
        em.persist(newcomer)
        assertSql()

        assertSame(newcomer, repository.findByName("lee")) // JPQL 발생 -> flush가 발생하며 INSERT가 먼저 실행된다.
        assertSql("insert", "select")
        assertEquals(1L, databaseCount(2L))
        assertTrue(em.contains(newcomer)) // flush는 clear도 commit도 아니다.
        assertSql("insert", "select", "select")
    }

    @Test
    fun `10 AUTO는 변경 감지 UPDATE를 SELECT보다 먼저 실행한다`() {
        val managed = repository.findById(1L).orElseThrow()
        managed.name = "changed"
        assertEquals("kim", databaseName()) // JDBC로 SQL을 직접 실행

        assertSame(managed, repository.findByName("changed")) // JPA로 인한 조회 -> 객체 변경 감지
        assertSql("select", "select", "update", "select")
        assertEquals("changed", databaseName())
        assertSql("select", "select", "update", "select", "select")
        // save를 다시 호출하지 않아도 관리 객체의 변경이 flush된다.
    }

    @Test
    fun `11 Hibernate AUTO는 관련 없는 테이블 조회 전 INSERT를 미룬다`() {
        em.persist(QueryStudyMember(2L, 102L, "lee")) // 영속화

        val notes = em.createQuery("select n from QueryStudyNote n", QueryStudyNote::class.java).resultList // JPQL 실행
        assertTrue(notes.isEmpty())
        assertSql("select")
        assertEquals(0L, databaseCount(2L)) // 아직 INSERT되지 않았다.

        assertNotNull(repository.findByName("lee")) // JPQL 발생 -> flush가 발생하며 INSERT가 먼저 실행된다.
        assertSql("select", "select", "insert", "select")
        // 쿼리와 변경 대상 테이블의 겹침을 보는 Hibernate의 최적화.
    }

    @Test
    fun `12 Hibernate COMMIT에서는 관리 객체가 있어도 DB 조건 조회에서 빠질 수 있다`() {
        em.flushMode = FlushModeType.COMMIT
        val newcomer = QueryStudyMember(2L, 102L, "lee")
        em.persist(newcomer) // 영속화

        assertSame(newcomer, repository.findById(2L).orElseThrow()) // 영속성 컨텍스트에서 조회
        assertNull(repository.findByName("lee")) // select 쿼리 실행. COMMIT 모드에서는 flush가 발생하지 않아 DB에 INSERT되지 않았다.
        assertSql("select")

        em.flush() // COMMIT 모드에서도 명시적 flush는 가능하다.
        assertSame(newcomer, repository.findByName("lee")) // JPQL 발생
        assertSql("select", "insert", "select")
        // COMMIT에서 미반영 변경이 쿼리에 미치는 영향은 JPA 명세상 unspecified.
        // 이 테스트는 Hibernate의 동작을 검증하며 다른 구현체에 일반화하지 않는다.
    }

    @Test
    fun `13 COMMIT에서 DB 조건과 반환 객체의 필드 값이 다를 수 있다`() {
        em.flushMode = FlushModeType.COMMIT
        val managed = repository.findById(1L).orElseThrow() // select
        managed.name = "changed" // 변경 감지. 아직 flush되지 않았다.

        val byOldName = assertNotNull(repository.findByName("kim")) // select
        assertSame(managed, byOldName) // 반환된 객체는 영속성 컨텍스트의 기존 관리 객체다.
        assertEquals("changed", byOldName.name) // 관리 객체의 필드 값은 이미 변경되었다.
        assertNull(repository.findByName("changed")) // select
        assertEquals("kim", databaseName()) // select
        assertSql("select", "select", "select", "select")
        // WHERE는 DB 값 kim으로 평가. 반환 엔티티는 메모리의 기존 changed 객체.
    }

    @Test
    fun `14 clear 후에는 같은 PK를 조회해도 새로운 관리 객체가 만들어진다`() {
        val first = repository.findById(1L).orElseThrow()
        em.clear()
        val second = repository.findById(1L).orElseThrow()

        assertSql("select", "select")
        assertEquals(first.memberId, second.memberId)
        assertNotSame(first, second)
        assertFalse(em.contains(first))
        assertTrue(em.contains(second))
    }

    @Test
    fun `15 clear는 flush하지 않으므로 미반영 변경을 잃을 수 있다`() {
        val first = repository.findById(1L).orElseThrow() // select
        first.name = "lost-change"
        em.clear() // 영속성 컨텍스트 초기화
        em.flush()

        val reloaded = repository.findById(1L).orElseThrow() // select
        assertEquals("kim", reloaded.name)
        assertEquals("lost-change", first.name)
        assertFalse(em.contains(first)) // clear로 관리 객체가 제거되었다.
        assertSql("select", "select")
    }

    @Test
    fun `16 벌크 UPDATE 후 엔티티 재조회는 refresh와 다르다`() {
        val managed = repository.findById(1L).orElseThrow() // select
        assertEquals(1, repository.renameInBulk(1L, "bulk-changed")) // 벌크 DML은 flush를 유발하지 않는다. (DB에 바로 적용)

        assertEquals("bulk-changed", databaseName()) // select
        assertEquals("kim", managed.name) // 벌크 DML은 관리 객체를 동기화하지 않는다. (flush가 발생하지 않아 영속성 컨텍스트의 값과 DB 값 사이의 불일치 발생)
        assertSame(managed, repository.findUsingJpql(1L)) // select -> 영속성 컨텍스트에서 기존 관리 객체를 반환
        assertEquals("kim", managed.name) // SELECT 재실행 자체는 refresh가 아니다.
        assertEquals("bulk-changed", repository.findNameUsingJpql(1L)) // 스칼라는 DB 값.
        assertSql("select", "update", "select", "select", "select")

        em.refresh(managed)
        assertEquals("bulk-changed", managed.name)
        assertTrue(em.contains(managed))
        assertSql("select", "update", "select", "select", "select", "select")
    }

    private fun databaseCount(memberId: Long): Long = jdbc.queryForObject(
        "select count(*) from query_study_members where member_id = ?",
        Long::class.java,
        memberId,
    )!!

    private fun databaseName(): String = jdbc.queryForObject(
        "select name from query_study_members where member_id = 1",
        String::class.java,
    )!!

    private fun assertSql(vararg expected: String) {
        val statements = executedSql()
        val actual = statements.map { it.substringBefore(' ').lowercase() }
        assertEquals(expected.toList(), actual, "관찰한 JDBC SQL:\n${statements.joinToString("\n")}")

        // 직접 센 값만 비교하지 않고 datasource-proxy의 종류별 집계도 검증한다.
        val count = QueryCountHolder.getGrandTotal()
        assertEquals(expected.size.toLong(), count.total)
        assertEquals(expected.count { it == "select" }.toLong(), count.select)
        assertEquals(expected.count { it == "insert" }.toLong(), count.insert)
        assertEquals(expected.count { it == "update" }.toLong(), count.update)
        assertEquals(expected.count { it == "delete" }.toLong(), count.delete)
        assertEquals(0L, count.failure)
    }

    // 별도 기록기 대신 Mockito가 보관한 datasource-proxy의 실행 완료 이벤트를 읽는다.
    private fun executedSql(): List<String> = mockingDetails(queryListener).invocations
        .filter { it.method.name == "afterQuery" }
        .flatMap { it.getArgument<List<QueryInfo>>(1) }
        .map { it.query.trim().replace(Regex("\\s+"), " ") }
}
