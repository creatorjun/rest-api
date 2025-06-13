package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@Entity
@Table(name = "events")
data class Event(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_uid",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_event_user_uid")
    )
    val user: User,

    @Column(name = "text", nullable = false, length = 1000)
    var text: String,

    @Column(name = "start_time")
    var startTime: LocalTime? = null,

    @Column(name = "end_time")
    var endTime: LocalTime? = null,

    @Column(name = "event_date")
    var eventDate: LocalDate? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

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