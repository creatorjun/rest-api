package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 요청 시 사용할 DTO
data class EventCreateRequestDto( // 클래스 이름 변경: MemoCreateRequestDto -> EventCreateRequestDto

    @field:NotBlank(message = "이벤트 내용은 비워둘 수 없습니다.") // 메시지 변경: 메모 -> 이벤트
    @field:Size(max = 1000, message = "이벤트 내용은 최대 1000자까지 입력 가능합니다.") // 메시지 변경: 메모 -> 이벤트
    val text: String,

    val startTime: String?, // "HH:mm" 형식
    val endTime: String?,   // "HH:mm" 형식
    val eventDate: String?  // 필드 이름 변경: date -> eventDate, "YYYY-MM-DD" 형식
) {
    fun getStartTimeAsLocalTime(): LocalTime? {
        return startTime?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }
    }

    fun getEndTimeAsLocalTime(): LocalTime? {
        return endTime?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME) }
    }

    fun getEventDateAsLocalDate(): LocalDate? { // 메소드 이름 변경 및 eventDate 필드 사용
        return eventDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
    }
}