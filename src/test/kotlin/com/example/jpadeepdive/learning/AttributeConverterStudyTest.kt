package com.example.jpadeepdive.learning

import com.example.jpadeepdive.learning.converter.ConverterProfile
import com.example.jpadeepdive.learning.converter.ConverterProfileRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttributeConverterStudyTest {

    @Autowired
    private lateinit var repository: ConverterProfileRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        repository.deleteAllInBatch()
    }

    @Test
    fun `엔티티의 문자열은 암호화되어 DB에 저장되고 조회할 때 자동 복호화된다`() {
        val plainText = "주민번호처럼 노출되면 안 되는 값"
        val saved = repository.saveAndFlush(
            ConverterProfile(
                name = "member-a",
                secretMemo = plainText,
                marketingAgreed = true,
            ),
        )

        // JDBC는 JPA Converter를 거치지 않으므로 DB에 저장된 원문을 그대로 볼 수 있다.
        val databaseValue = jdbcTemplate.queryForObject(
            "select secret_memo from converter_profiles where id = ?",
            String::class.java,
            saved.id,
        )
        assertNotEquals(plainText, databaseValue)
        assertFalse(databaseValue!!.contains(plainText))

        // 1차 캐시를 비워 반드시 DB에서 다시 읽게 해도 엔티티에는 복호화된 값이 들어온다.
        entityManager.clear()
        val reloaded = repository.findById(saved.id).orElseThrow()
        assertEquals(plainText, reloaded.secretMemo)
    }

    @Test
    fun `Boolean true와 false는 DB에 각각 Y와 N으로 저장된다`() {
        val agreed = repository.saveAndFlush(
            ConverterProfile("agreed", "memo-a", marketingAgreed = true),
        )
        val disagreed = repository.saveAndFlush(
            ConverterProfile("disagreed", "memo-b", marketingAgreed = false),
        )

        val storedValues = jdbcTemplate.queryForList(
            "select marketing_agreed from converter_profiles order by id",
            String::class.java,
        )
        assertEquals(listOf("Y", "N"), storedValues)

        entityManager.clear()
        assertTrue(repository.findById(agreed.id).orElseThrow().marketingAgreed)
        assertFalse(repository.findById(disagreed.id).orElseThrow().marketingAgreed)
    }

    @Test
    fun `같은 평문도 매번 새로운 IV를 사용하므로 서로 다른 암호문으로 저장된다`() {
        val first = repository.saveAndFlush(
            ConverterProfile("first", "same-secret", marketingAgreed = true),
        )
        val second = repository.saveAndFlush(
            ConverterProfile("second", "same-secret", marketingAgreed = true),
        )

        val encryptedValues = jdbcTemplate.queryForList(
            "select secret_memo from converter_profiles order by id",
            String::class.java,
        )
        assertNotEquals(encryptedValues[0], encryptedValues[1])

        entityManager.clear()
        assertEquals("same-secret", repository.findById(first.id).orElseThrow().secretMemo)
        assertEquals("same-secret", repository.findById(second.id).orElseThrow().secretMemo)
    }
}
