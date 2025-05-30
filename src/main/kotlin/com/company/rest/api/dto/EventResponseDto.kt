package com.company.rest.api.dto

import com.company.rest.api.entity.Event // 엔티티 임포트 변경: Memo -> Event
import java.time.format.DateTimeFormatter

data class EventResponseDto( // 클래스 이름 변경: MemoResponseDto -> EventResponseDto
    val backendEventId: String, // 필드 이름 변경: backendMemoId -> backendEventId
    val text: String,
    val startTime: String?, // "HH:mm"
    val endTime: String?,   // "HH:mm"
    val createdAt: String,  // ISO8601
    val eventDate: String?       // 필드 이름 변경: date -> eventDate, "YYYY-MM-DD"
) {
    companion object {
        fun fromEntity(event: Event): EventResponseDto { // 파라미터 타입 및 이름 변경: memo: Memo -> event: Event
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD

            return EventResponseDto(
                backendEventId = event.id, // 필드 매핑 변경: backendMemoId = memo.id -> backendEventId = event.id
                text = event.text,
                startTime = event.startTime?.format(timeFormatter),
                endTime = event.endTime?.format(timeFormatter),
                createdAt = event.createdAt.format(DateTimeFormatter.ISO_DATE_TIME),
                eventDate = event.eventDate?.format(dateFormatter) // 필드 매핑 변경: date = memo.memoDate -> eventDate = event.eventDate
            )
        }
    }
}