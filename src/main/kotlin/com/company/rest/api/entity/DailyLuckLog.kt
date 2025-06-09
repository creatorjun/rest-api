package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "daily_luck_logs", uniqueConstraints = [ // 테이블 이름 변경
        UniqueConstraint(name = "uk_daily_luck_log_request_date", columnNames = ["request_date"])
    ]
)
data class DailyLuckLog( // 클래스 이름 변경
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "request_date", nullable = false, updatable = false)
    val requestDate: LocalDate, // 운세를 요청한 대상 날짜

    @Column(name = "question_asked", columnDefinition = "TEXT", nullable = false, updatable = false)
    val questionAsked: String, // 제미나이에게 실제로 보낸 질문

    @Column(name = "raw_response", columnDefinition = "TEXT", nullable = true)
    var rawResponse: String? = null, // 제미나이로부터 받은 원본 응답 (JSON 문자열)

    @Enumerated(EnumType.STRING)
    @Column(name = "parsing_status", nullable = false)
    var parsingStatus: LuckParsingStatus = LuckParsingStatus.PENDING, // 열거형 이름 변경

    @OneToMany(mappedBy = "logEntry", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var luckEntries: MutableList<ZodiacSignLuck> = mutableListOf(), // 필드명 및 타입 변경 (ZodiacSignLuck는 다음 단계에서 생성)

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    // 양방향 연관관계 편의 메소드
    fun addZodiacSignLuck(luckEntry: ZodiacSignLuck) { // 메소드명 및 파라미터 타입 변경
        luckEntries.add(luckEntry)
        luckEntry.logEntry = this
    }

    fun removeZodiacSignLuck(luckEntry: ZodiacSignLuck) { // 메소드명 및 파라미터 타입 변경
        luckEntries.remove(luckEntry)
        luckEntry.logEntry = null
    }
}