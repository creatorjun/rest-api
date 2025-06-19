package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "anniversaries",
    indexes = [Index(name = "idx_anniversary_user_id", columnList = "user_uid")]
)
data class Anniversary(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_uid",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_anniversary_user_uid")
    )
    val user: User, // 이 기념일을 생성한 사용자

    @Column(name = "title", nullable = false, length = 100)
    var title: String,

    @Column(name = "anniversary_date", nullable = false)
    var date: LocalDate,

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