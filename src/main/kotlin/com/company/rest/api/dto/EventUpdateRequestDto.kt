package com.company.rest.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class EventUpdateRequestDto(
    @field:NotBlank(message = "이벤트 ID는 비워둘 수 없습니다.")
    val eventId: String,

    @field:Size(max = 1000, message = "이벤트 내용은 최대 1000자까지 입력 가능합니다.")
    val text: String?,

    val startTime: String?,
    val endTime: String?,
    val eventDate: String?,

    @field:NotNull(message = "displayOrder는 필수 값입니다.")
    @field:Min(value = 0, message = "displayOrder는 0 이상이어야 합니다.")
    val displayOrder: Int?
) {
    fun getStartTimeAsLocalTime(): LocalTime? {
        return startTime?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalTime.parse(it, DateTimeFormatter.ISO_LOCAL_TIME)
            } catch (e: DateTimeParseException) {
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

    fun getEventDateAsLocalDate(): LocalDate? {
        return eventDate?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}