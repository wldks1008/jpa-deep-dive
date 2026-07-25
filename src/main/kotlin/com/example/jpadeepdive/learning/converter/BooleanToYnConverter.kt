package com.example.jpadeepdive.learning.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class BooleanToYnConverter : AttributeConverter<Boolean, String> {

    override fun convertToDatabaseColumn(attribute: Boolean?): String? {
        return when (attribute) {
            true -> "Y"
            false -> "N"
            null -> null
        }
    }

    override fun convertToEntityAttribute(dbData: String?): Boolean? {
        return when (dbData) {
            "Y" -> true
            "N" -> false
            null -> null
            else -> throw IllegalArgumentException("Y/N만 Boolean으로 변환할 수 있습니다: $dbData")
        }
    }
}
