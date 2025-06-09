package com.company.rest.api.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "zodiac_sign_luck_entries") // 테이블 이름 변경
data class ZodiacSignLuck( // 클래스 이름 변경
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "log_entry_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_luck_entry_log_entry")
    ) // ForeignKey 및 참조 컬럼명 변경
    var logEntry: DailyLuckLog? = null, // 부모 엔티티 타입 변경

    @Column(name = "zodiac_name", nullable = false)
    val zodiacName: String, // 띠 이름 (예: "쥐띠")

    @Column(name = "applicable_years_json", columnDefinition = "TEXT", nullable = true)
    val applicableYearsJson: String?, // 해당 년도 목록 (JSON 배열 문자열)

    @Column(name = "overall_luck", columnDefinition = "TEXT", nullable = true)
    val overallLuck: String?, // 오늘의 총운

    @Column(name = "financial_luck", columnDefinition = "TEXT", nullable = true)
    val financialLuck: String?, // 금전운

    @Column(name = "love_luck", columnDefinition = "TEXT", nullable = true)
    val loveLuck: String?, // 애정운

    @Column(name = "health_luck", columnDefinition = "TEXT", nullable = true)
    val healthLuck: String?, // 건강운

    @Column(name = "lucky_number", nullable = true)
    val luckyNumber: Int?, // 행운의 숫자

    @Column(name = "lucky_color", nullable = true)
    val luckyColor: String?, // 행운의 색상

    @Column(name = "advice", columnDefinition = "TEXT", nullable = true)
    val advice: String? // 오늘의 조언
)