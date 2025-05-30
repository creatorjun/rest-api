package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class EventUpdateRequestDto( // 클래스 이름 변경: MemoUpdateRequestDto -> EventUpdateRequestDto
    @field:NotBlank(message = "이벤트 ID는 비워둘 수 없습니다.") // 필드 이름 및 메시지 변경: memoId -> eventId, 메모 -> 이벤트
    val eventId: String,

    @field:Size(max = 1000, message = "이벤트 내용은 최대 1000자까지 입력 가능합니다.") // 메시지 변경: 메모 -> 이벤트
    val text: String?, // 변경하려는 경우에만 값을 포함

    val startTime: String?, // "HH:mm" 형식, 변경하려는 경우에만 값을 포함
    val endTime: String?,   // "HH:mm" 형식, 변경하려는 경우에만 값을 포함
    val eventDate: String?       // 필드 이름 변경: date -> eventDate, "YYYY-MM-DD" 형식, 변경하려는 경우에만 값을 포함
) {
    // 각 시간/날짜 필드를 LocalTime/LocalDate로 변환하는 헬퍼 메소드
    // 유효하지 않은 형식이면 null을 반환하거나 예외를 던질 수 있음 (여기서는 null 반환 시도)
    fun getStartTimeAsLocalTime(): LocalTime? {
        return startTime?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME)
            } catch (e: DateTimeParseException) {
                // 유효하지 않은 형식의 경우 null 반환 또는 예외 처리
                // 여기서는 null을 반환하여 해당 필드를 업데이트하지 않도록 유도 가능
                // 또는 서비스 레이어에서 더 명확한 예외를 던지도록 처리
                null
            }
        }
    }

    fun getEndTimeAsLocalTime(): LocalTime? {
        return endTime?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }

    fun getEventDateAsLocalDate(): LocalDate? { // 메소드 이름 변경 및 eventDate 필드 사용
        return eventDate?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}