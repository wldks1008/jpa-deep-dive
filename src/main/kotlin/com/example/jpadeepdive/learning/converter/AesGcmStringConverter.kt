package com.example.jpadeepdive.learning.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 학습용 문자열 암호화 Converter.
 * 매번 새로운 IV를 사용하므로 같은 평문도 서로 다른 암호문으로 저장된다.
 */
@Converter
class AesGcmStringConverter : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null

        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(LEARNING_KEY, "AES"),
                GCMParameterSpec(TAG_LENGTH_BITS, iv),
            )
        }
        val encrypted = cipher.doFinal(attribute.toByteArray(StandardCharsets.UTF_8))

        // 복호화할 때 IV가 필요하므로 "IV + 암호문"을 하나로 합쳐 Base64로 저장한다.
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null

        val decoded = Base64.getDecoder().decode(dbData)
        require(decoded.size > IV_LENGTH) { "유효하지 않은 암호문입니다." }

        val iv = decoded.copyOfRange(0, IV_LENGTH)
        val encrypted = decoded.copyOfRange(IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(LEARNING_KEY, "AES"),
                GCMParameterSpec(TAG_LENGTH_BITS, iv),
            )
        }

        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private val secureRandom = SecureRandom()

        // AES-128 학습용 고정 키(16바이트). 운영 코드에서 이렇게 키를 하드코딩하면 안 된다.
        private val LEARNING_KEY = "jpa-study-key-16".toByteArray(StandardCharsets.UTF_8)
    }
}
