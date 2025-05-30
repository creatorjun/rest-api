package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "events") // 테이블 이름 변경: memos -> events
data class Event( // 클래스 이름 변경: Memo -> Event
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uid", nullable = false, foreignKey = ForeignKey(name = "fk_event_user_uid")) // ForeignKey 이름 변경: fk_memo_user_uid -> fk_event_user_uid
    val user: User, // 작성자 정보

    @Column(name = "text", nullable = false, length = 1000)
    var text: String,

    @Column(name = "start_time")
    var startTime: LocalTime? = null,

    @Column(name = "end_time")
    var endTime: LocalTime? = null,

    @Column(name = "event_date") // 필드 이름 변경: memoDate -> eventDate
    var eventDate: LocalDate? = null, // Flutter 앱의 'date' 필드에 해당 (이제 'eventDate'로 명명)

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}