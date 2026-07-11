package com.example.jpadeepdive.learning

import com.example.jpadeepdive.learning.sync.IncomingOrderBatch
import com.example.jpadeepdive.learning.sync.IncomingOrderLine
import com.example.jpadeepdive.learning.sync.OrderPublishMapper
import com.example.jpadeepdive.learning.sync.OrderSyncService
import org.hibernate.LazyInitializationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
class LazyLoadingStudyTest {

    @Autowired
    private lateinit var orderSyncService: OrderSyncService

    @Autowired
    private lateinit var orderPublishMapper: OrderPublishMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("delete from orders")
        jdbcTemplate.update("delete from members")
        jdbcTemplate.update("delete from organizations")
    }

    @Test
    fun `LAZY 연관을 fetch join 없이 트랜잭션 밖에서 접근하면 예외가 발생한다`() {
        // 공부 point: @ManyToOne(fetch = LAZY)는 트랜잭션 안에서 프록시만 들고 나올 수 있다.
        // 트랜잭션이 끝난 뒤 프록시의 필드를 처음 읽으려 하면 LazyInitializationException이 발생한다.
        val memberId = seedMemberGraph()
        seedOrder(memberId = memberId, orderCode = "order-a")

        val orders = orderSyncService.findOrdersWithoutFetchJoin(memberId)

        assertThrows<LazyInitializationException> {
            orders.single().member.uid
        }
    }

    @Test
    fun `fetch join으로 필요한 연관을 미리 가져오면 트랜잭션 밖에서도 이미 로딩된 값을 읽을 수 있다`() {
        // 공부 point: fetch join은 프록시를 나중에 초기화하는 것이 아니라 조회 시점에 필요한 연관을 함께 로딩한다.
        // 그래서 트랜잭션 밖에서도 이미 로딩된 member와 organization의 기본 필드를 읽을 수 있다.
        val memberId = seedMemberGraph()
        seedOrder(memberId = memberId, orderCode = "order-a")

        val orders = orderSyncService.findOrdersWithMemberAndOrganization(memberId)

        assertEquals("member-1", orders.single().member.uid)
        assertEquals("org-1", orders.single().member.organization.code)
    }

    @Test
    fun `detached 엔티티라도 이미 로딩된 기본 필드와 연관은 읽을 수 있다`() {
        // 공부 point: prepareBatch에서 조회한 member는 메서드가 끝나면 detached 상태가 된다.
        // 하지만 fetch join으로 organization까지 초기화해 두면 DTO가 들고 있는 detached 객체의 로딩된 값은 읽을 수 있다.
        seedMemberGraph()

        val preparedBatch = orderSyncService.prepareBatch(
            IncomingOrderBatch(
                memberUid = "member-1",
                lines = listOf(incomingLine("order-a")),
            ),
        )

        val member = preparedBatch.lines.single().member
        assertEquals("member-1", member.uid)
        assertEquals("org-1", member.organization.code)
    }

    @Test
    fun `DTO에 없는 기존 엔티티까지 반환하면 교체되지 않은 LAZY parent 때문에 mapper에서 예외가 발생한다`() {
        // 공부 point: DB에는 a,b,c가 있고 요청은 a,b만 있을 때 c는 applyTo를 타지 않는다.
        // 따라서 c.member는 fetch join 없이 조회된 LAZY 프록시 그대로이고, 트랜잭션 밖 mapper가 c.member를 읽으면 터진다.
        val memberId = seedMemberGraph()
        seedOrder(memberId = memberId, orderCode = "order-a")
        seedOrder(memberId = memberId, orderCode = "order-b")
        seedOrder(memberId = memberId, orderCode = "order-c")

        val preparedBatch = orderSyncService.prepareBatch(
            IncomingOrderBatch(
                memberUid = "member-1",
                lines = listOf(
                    incomingLine("order-a", productName = "changed-a"),
                    incomingLine("order-b", productName = "changed-b"),
                ),
            ),
        )

        val unsafeOrders = orderSyncService.synchronizeWithoutRequestFiltering(preparedBatch)

        assertThrows<LazyInitializationException> {
            orderPublishMapper.mapForPublish(unsafeOrders)
        }
    }

    @Test
    fun `요청 항목만 반환하면 applyTo에서 parent 참조가 교체된 엔티티만 mapper로 넘어간다`() {
        // 공부 point: applyTo는 LAZY 프록시를 로딩하는 것이 아니라 DTO가 들고 있던 detached member로 참조를 교체한다.
        // 요청과 매칭된 a,b만 반환하면 mapper는 교체된 member를 읽으므로 안전하다.
        val memberId = seedMemberGraph()
        seedOrder(memberId = memberId, orderCode = "order-a")
        seedOrder(memberId = memberId, orderCode = "order-b")
        seedOrder(memberId = memberId, orderCode = "order-c")

        val preparedBatch = orderSyncService.prepareBatch(
            IncomingOrderBatch(
                memberUid = "member-1",
                lines = listOf(
                    incomingLine("order-a", productName = "changed-a"),
                    incomingLine("order-b", productName = "changed-b"),
                ),
            ),
        )

        val synchronizedOrders = orderSyncService.synchronize(preparedBatch)
        val messages = orderPublishMapper.mapForPublish(synchronizedOrders)

        assertEquals(listOf("order-a", "order-b"), messages.map { it.orderCode }.sorted())
        assertEquals(listOf("member-1", "member-1"), messages.map { it.parent?.uid.orEmpty() }.sorted())
        assertEquals(listOf("org-1", "org-1"), messages.map { it.parent?.parent?.code.orEmpty() }.sorted())
    }

    @Test
    fun `JDBC bulk insert 후 재조회한 엔티티는 parent 참조를 다시 주입하지 않으면 mapper에서 예외가 발생한다`() {
        // 공부 point: JdbcTemplate batchUpdate는 JPA 영속성 컨텍스트에 엔티티를 managed 상태로 넣지 않는다.
        // bulk insert 후 다시 조회한 엔티티의 member는 LAZY 프록시이므로 반환 전 replaceMemberReference가 없으면 mapper에서 터진다.
        seedMemberGraph()
        val preparedBatch = orderSyncService.prepareBatch(
            IncomingOrderBatch(
                memberUid = "member-1",
                lines = listOf(incomingLine("order-a")),
            ),
        )

        val orders = orderSyncService.synchronizeUsingBulkWithoutReferenceReplacement(preparedBatch)

        assertThrows<LazyInitializationException> {
            orderPublishMapper.mapForPublish(orders)
        }
    }

    @Test
    fun `JDBC bulk insert 후 재조회한 엔티티에 parent 참조를 다시 주입하면 mapper가 안전하다`() {
        // 공부 point: bulk insert 후 재조회한 엔티티도 replaceMemberReference로 detached member를 꽂으면
        // 트랜잭션 밖 mapper가 LAZY 프록시 대신 이미 준비된 객체 그래프를 읽게 된다.
        seedMemberGraph()
        val preparedBatch = orderSyncService.prepareBatch(
            IncomingOrderBatch(
                memberUid = "member-1",
                lines = listOf(incomingLine("order-a")),
            ),
        )

        val orders = orderSyncService.synchronizeUsingBulk(preparedBatch)
        val messages = orderPublishMapper.mapForPublish(orders)

        assertEquals("order-a", messages.single().orderCode)
        assertEquals("member-1", messages.single().parent?.uid)
        assertEquals("org-1", messages.single().parent?.parent?.code)
    }

    @Test
    fun `트랜잭션 안에서 엔티티 필드만 변경해도 dirty checking으로 update 된다`() {
        // 공부 point: managed 엔티티는 트랜잭션 안에서 필드 변경만 해도 flush 시점에 update SQL이 나간다.
        // 명시적으로 repository.save를 다시 호출하지 않아도 DB 값이 변경되는 것이 dirty checking이다.
        val memberId = seedMemberGraph()
        val orderId = seedOrder(memberId = memberId, orderCode = "order-a", status = "READY")

        orderSyncService.changeStatusByDirtyChecking(orderId, "DONE")

        val status = jdbcTemplate.queryForObject(
            "select status from orders where id = ?",
            String::class.java,
            orderId,
        )
        assertEquals("DONE", status)
    }

    private fun seedMemberGraph(): Long {
        jdbcTemplate.update("insert into organizations (code, name) values (?, ?)", "org-1", "Organization 1")
        val organizationId = jdbcTemplate.queryForObject(
            "select id from organizations where code = ?",
            Long::class.java,
            "org-1",
        ) ?: error("organization was not inserted")

        jdbcTemplate.update(
            "insert into members (organization_id, uid, name) values (?, ?, ?)",
            organizationId,
            "member-1",
            "Member 1",
        )

        return jdbcTemplate.queryForObject(
            "select id from members where uid = ?",
            Long::class.java,
            "member-1",
        ) ?: error("member was not inserted")
    }

    private fun seedOrder(
        memberId: Long,
        orderCode: String,
        productName: String = "before-$orderCode",
        amount: BigDecimal = BigDecimal("100.00"),
        status: String = "READY",
    ): Long {
        jdbcTemplate.update(
            """
            insert into orders (member_id, order_code, product_name, amount, status)
            values (?, ?, ?, ?, ?)
            """.trimIndent(),
            memberId,
            orderCode,
            productName,
            amount,
            status,
        )

        return jdbcTemplate.queryForObject(
            "select id from orders where order_code = ?",
            Long::class.java,
            orderCode,
        ) ?: error("order was not inserted")
    }

    private fun incomingLine(
        orderCode: String,
        productName: String = "product-$orderCode",
        amount: BigDecimal = BigDecimal("200.00"),
        status: String = "SYNCED",
    ): IncomingOrderLine {
        return IncomingOrderLine(
            orderCode = orderCode,
            productName = productName,
            amount = amount,
            status = status,
        )
    }
}
