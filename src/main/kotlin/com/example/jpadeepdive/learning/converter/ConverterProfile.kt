package com.example.jpadeepdive.learning.converter

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "converter_profiles")
class ConverterProfile(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Convert(converter = AesGcmStringConverter::class)
    @Column(name = "secret_memo", nullable = false, length = 500)
    var secretMemo: String,

    @Convert(converter = BooleanToYnConverter::class)
    @Column(name = "marketing_agreed", nullable = false, length = 1)
    var marketingAgreed: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0L
        protected set
}
