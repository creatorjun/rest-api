package com.company.rest.api.dto

import com.company.rest.api.entity.Event
import java.time.format.DateTimeFormatter

data class EventResponseDto(
    val backendEventId: String,
    val text: String,
    val startTime: String?,
    val endTime: String?,
    val createdAt: String,
    val eventDate: String?,
    val displayOrder: Int
) {
    companion object {
        fun fromEntity(event: Event): EventResponseDto {
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

            return EventResponseDto(
                backendEventId = event.id,
                text = event.text,
                startTime = event.startTime?.format(timeFormatter),
                endTime = event.endTime?.format(timeFormatter),
                createdAt = event.createdAt.format(DateTimeFormatter.ISO_DATE_TIME),
                eventDate = event.eventDate?.format(dateFormatter),
                displayOrder = event.displayOrder
            )
        }
    }
}